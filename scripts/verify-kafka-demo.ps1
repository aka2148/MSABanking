$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $envFile = Join-Path $repoRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) { throw 'Run scripts/initialize-demo-env.ps1 first.' }
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^([^=]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
    }
    $mysqlUser = $settings['MYSQL_APP_USER']
    $mysqlPassword = $settings['MYSQL_APP_PASSWORD']
    $adminUsername = $settings['DEMO_ADMIN_USERNAME']
    $adminPassword = $settings['DEMO_ADMIN_PASSWORD']
    if (-not $adminUsername -or -not $adminPassword) { throw 'Run scripts/initialize-demo-env.ps1 to configure the local demo admin.' }
    $apiBase = 'http://localhost:8080'

    function Invoke-DbScalar([string] $service, [string] $database, [string] $query) {
        $result = & docker compose exec -T $service mysql "--user=$mysqlUser" "--password=$mysqlPassword" "--database=$database" --batch --skip-column-names "--execute=$query"
        if ($LASTEXITCODE -ne 0) { throw "MySQL query failed for $database." }
        return (($result | Out-String).Trim())
    }

    function Invoke-DbCommand([string] $service, [string] $database, [string] $query) {
        & docker compose exec -T $service mysql "--user=$mysqlUser" "--password=$mysqlPassword" "--database=$database" "--execute=$query" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "MySQL command failed for $database." }
    }

    function Wait-ForSql([string] $service, [string] $database, [string] $query, [string] $expected, [string] $label) {
        $deadline = (Get-Date).AddSeconds(60)
        do {
            try { if ((Invoke-DbScalar $service $database $query) -eq $expected) { return } } catch { }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        throw "Timed out waiting for $label."
    }

    function Wait-ForHttp([string] $url) {
        $deadline = (Get-Date).AddSeconds(120)
        do {
            try { Invoke-RestMethod -Method Get -Uri $url -TimeoutSec 3 | Out-Null; return } catch { Start-Sleep -Seconds 2 }
        } while ((Get-Date) -lt $deadline)
        throw "Service did not become healthy: $url"
    }

    function Invoke-JsonPost([string] $url, $body, [string] $token = $null, [hashtable] $extraHeaders = @{}) {
        $headers = @{}
        if ($token) { $headers.Authorization = "Bearer $token" }
        foreach ($name in $extraHeaders.Keys) { $headers[$name] = $extraHeaders[$name] }
        return Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 12 -Compress) -TimeoutSec 20
    }

    foreach ($port in 8081, 8082, 8083) { Wait-ForHttp "http://localhost:$port/actuator/health" }
    Wait-ForHttp "$apiBase/gateway/health"
    Write-Output 'Gateway and services are healthy.'

    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $registered = Invoke-JsonPost "$apiBase/api/v1/auth/register" @{
        username = "kafka_$suffix"; password = 'DemoPassword123'; email = "kafka_$suffix@example.test"; fullName = 'Kafka Demo Customer'
    }
    $customerToken = $registered.token
    $customerPayload = $customerToken.Split('.')[1].Replace('-','+').Replace('_','/')
    switch ($customerPayload.Length % 4) { 2 { $customerPayload += '==' } 3 { $customerPayload += '=' } }
    $customerClaims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($customerPayload)) | ConvertFrom-Json
    $customerId = [long]$customerClaims.userId
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_user_ref WHERE fur_user_id=$customerId AND fur_source_version>=1 AND fur_username='$($registered.username)'" '1' 'the Identity user snapshot in FD'
    Write-Output 'Identity user.snapshot published and applied to FD.'

    $adminLogin = Invoke-JsonPost "$apiBase/api/v1/auth/login" @{ username = $adminUsername; password = $adminPassword }
    if (-not $adminLogin.token -or $adminLogin.roles -notcontains 'ROLE_ADMIN') { throw 'Configured demo admin could not authenticate with the required role.' }
    $adminToken = $adminLogin.token
    Write-Output 'Configured demo administrator authenticated through Identity.'
    $customerPage = Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/customers?search=$([uri]::EscapeDataString($registered.username))" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20
    $customerRecord = $customerPage.content | Where-Object { $_.username -eq $registered.username } | Select-Object -First 1
    if (-not $customerRecord) { throw 'Staff customer search did not return the newly registered customer.' }
    if (($customerRecord | ConvertTo-Json -Compress) -match 'password|credential|email') { throw 'Customer admin response exposed authentication or private email fields.' }
    $customerUpdate = @{ fullName = 'Kafka Demo Managed Customer'; kycStatus = 'VERIFIED'; riskCategory = 'LOW'; legalEntityType = 'INDIVIDUAL' } | ConvertTo-Json -Compress
    $updatedCustomer = Invoke-RestMethod -Method Put -Uri "$apiBase/api/v1/customers/$($customerRecord.id)" -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -Body $customerUpdate -TimeoutSec 20
    if ($updatedCustomer.kycStatus -ne 'VERIFIED' -or $updatedCustomer.fullName -ne 'Kafka Demo Managed Customer') { throw 'Staff customer update did not persist its managed profile fields.' }
    Write-Output 'Staff customer search and profile update passed without returning credentials.'
    $portfolio = Invoke-RestMethod -Method Get -Uri "$apiBase/api/reports/portfolio" -Headers @{ Authorization = "Bearer $customerToken" } -TimeoutSec 20
    if ($portfolio.customerUsername -ne $registered.username) { throw "Customer portfolio did not resolve $($registered.username)'s Identity snapshot." }
    $staffPortfolio = Invoke-RestMethod -Method Get -Uri "$apiBase/api/reports/portfolio/$([uri]::EscapeDataString($registered.username))" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20
    if ($staffPortfolio.customerUsername -ne $registered.username) { throw 'Staff customer portfolio lookup did not resolve by username.' }
    $operational = Invoke-RestMethod -Method Get -Uri "$apiBase/api/reports/operational" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20
    if ($null -eq $operational.currencies -or $null -eq $operational.maturingSoonAccounts) { throw 'Operational report did not return currency-grouped totals and upcoming maturities.' }
    $estimate = Invoke-JsonPost "$apiBase/api/calculator/estimate" @{ principal = 10000; baseInterestRate = 5; termDays = 365; category = 'SENIOR_CITIZEN'; compoundingFrequency = 'QUARTERLY' } $customerToken
    if ($estimate.effectiveInterestRate -ne 5.5 -or $estimate.maturityAmount -le 10000) { throw 'FD calculator did not apply the category boost and return a valid estimate.' }
    Write-Output 'Customer portfolio, staff operational reports, and FD calculator passed through the gateway.'

    $category = Invoke-JsonPost "$apiBase/api/v1/product-categories" @{ categoryCode = "FD$suffix"; categoryName = 'Fixed Deposit' } $adminToken
    if ((Invoke-DbScalar 'product-db' 'moneypal_product' "SELECT COUNT(*) FROM currency WHERE cur_iso_code='USD'") -eq '0') {
        Invoke-JsonPost "$apiBase/api/v1/currencies" @{ isoCode = 'USD'; currencyName = 'US Dollar'; currencySymbol = '$'; decimalPlaces = 2 } $adminToken | Out-Null
    }
    if ((Invoke-DbScalar 'product-db' 'moneypal_product' "SELECT COUNT(*) FROM currency WHERE cur_iso_code='JPY'") -eq '0') {
        Invoke-JsonPost "$apiBase/api/v1/currencies" @{ isoCode = 'JPY'; isoNumericCode = '392'; currencyName = 'Japanese Yen'; currencySymbol = '¥'; decimalPlaces = 0; active = $true } $adminToken | Out-Null
    }
    $offer = Invoke-JsonPost "$apiBase/api/v1/fd-products" @{
        productCode = "FD-$suffix"; productName = 'Kafka Demo Fixed Deposit'; categoryId = $category.categoryId; baseCurrencyCode = 'USD'
        currencies = @(
            @{ currencyCode = 'USD'; minimumAmount = 10; maximumAmount = 100000; prematurePenaltyPoints = 1.0; rateBands = @(@{ minimumTenureMonths = 1; maximumTenureMonths = 12; annualRate = 0.05 }) },
            @{ currencyCode = 'JPY'; minimumAmount = 1000; maximumAmount = 10000000; prematurePenaltyPoints = 1.0; rateBands = @(@{ minimumTenureMonths = 1; maximumTenureMonths = 12; annualRate = 0.05 }) }
        )
    } $adminToken
    $catalogProductId = [long]$offer.catalog_product_id
    Invoke-RestMethod -Method Put -Uri "$apiBase/api/v1/products/$catalogProductId/translations/es" -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -Body (@{ productName = 'Depósito a plazo de demostración'; description = 'Oferta traducida para verificación.' } | ConvertTo-Json -Compress) -TimeoutSec 20 | Out-Null
    $spanishProduct = Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/products/${catalogProductId}?lang=es" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20
    if ($spanishProduct.productName -ne 'Depósito a plazo de demostración') { throw 'Product translation did not resolve for the requested locale.' }
    Invoke-RestMethod -Method Put -Uri "$apiBase/api/v1/fd-products/$catalogProductId" -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -Body (@{ productName = 'Kafka Demo FD Updated'; description = 'Updated demo fixed deposit.' } | ConvertTo-Json -Compress) -TimeoutSec 20 | Out-Null
    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/fd-products/$($offer.catalog_product_id)/activate" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20 | Out-Null
    $productId = [long]$offer.catalog_product_id
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_product_ref WHERE fpr_product_id=$productId AND fpr_source_version>=1" '1' 'the Product catalog snapshot in FD'
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_product_ref WHERE fpr_product_id=$productId AND fpr_product_name='Kafka Demo FD Updated'" '1' 'the updated product details in FD'
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_product_currency_ref pc JOIN fd_currency_ref c ON c.fcr_currency_id=pc.currency_id WHERE pc.product_id=$productId AND c.fcr_iso_code='USD' AND c.fcr_decimal_places=2 AND pc.premature_penalty_points=1.0000" '1' 'the product currency/penalty reference in FD'
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_product_currency_ref pc JOIN fd_currency_ref c ON c.fcr_currency_id=pc.currency_id WHERE pc.product_id=$productId AND c.fcr_iso_code='JPY' AND c.fcr_decimal_places=0" '1' 'the zero-decimal JPY reference in FD'
    $eligible = Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/fd-products/eligible?lang=en" -Headers @{ Authorization = "Bearer $customerToken" } -TimeoutSec 20
    $customerOffer = @($eligible | Where-Object { $_.product_id -eq $productId }) | Select-Object -First 1
    $yenTerms = @($customerOffer.currency_offers | Where-Object { $_.currency_code -eq 'JPY' }) | Select-Object -First 1
    if (-not $yenTerms -or $yenTerms.decimal_places -ne 0) { throw 'The customer offer API did not retain the JPY zero-decimal currency scale.' }
    $staffOnlyDenied = $false
    try { Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/customers" -Headers @{ Authorization = "Bearer $customerToken" } -TimeoutSec 20 | Out-Null }
    catch { $staffOnlyDenied = ([int]$_.Exception.Response.StatusCode -eq 403) }
    if (-not $staffOnlyDenied) { throw 'Customer-role access was not denied on the staff customer API.' }
    Write-Output 'Product updates/translations and USD/JPY currency scales passed; versioned product.catalog snapshots applied to FD.'

    $savings = Invoke-JsonPost "$apiBase/api/v1/savings-accounts" @{ currencyCode = 'USD'; displayName = 'Kafka Demo Savings' } $customerToken
    # This local-only opening credit stands in for an external deposit rail, which is out of scope for the demo.
    $savingsId = [long]$savings.account_id
    Invoke-JsonPost "$apiBase/api/v1/savings-accounts/$savingsId/demo-opening" @{ amount = 100.00 } $customerToken @{ 'Idempotency-Key' = "kafka-opening-$suffix" } | Out-Null
    $booking = Invoke-JsonPost "$apiBase/api/v1/fixed-deposits" @{
        productId = $productId; savingsAccountId = $savingsId; principal = 100.00; currencyCode = 'USD'; tenureMonths = 1
    } $customerToken @{ 'Idempotency-Key' = "kafka-book-$suffix" }
    $fundHeaders = @{ 'Idempotency-Key' = "kafka-fund-$suffix" }
    try {
        Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/fixed-deposits/$($booking.account_id)/fund" -Headers (@{ Authorization = "Bearer $customerToken" } + $fundHeaders) -TimeoutSec 20 | Out-Null
    } catch {
        $body = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "FD funding failed: $body"
    }
    $fdId = [long]$booking.account_id
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_account_events WHERE fae_account_id=$fdId AND fae_is_published=1" '2' 'published FD booking/funding events'

    # Move only this disposable demo account's dates into the past to exercise batch accrual and maturity now.
    Invoke-DbCommand 'fd-db' 'moneypal_fd' "UPDATE fd_accounts SET fa_open_date=DATE_SUB(UTC_DATE(),INTERVAL 31 DAY), fa_maturity_date=DATE_SUB(UTC_DATE(),INTERVAL 1 DAY) WHERE fa_account_id=$fdId AND fa_status='ACTIVE'"
    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/admin/fixed-deposits/accrual-batch" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20 | Out-Null
    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/admin/fixed-deposits/maturity-batch" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20 | Out-Null
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_accounts WHERE fa_account_id=$fdId AND fa_status='MATURED'" '1' 'FD maturity batch'
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_account_events WHERE fae_account_id=$fdId AND fae_event_type IN ('BOOKED','FUNDED','INTEREST_ACCRUED','MATURED') AND fae_is_published=1" '4' 'published FD events through maturity'
    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/fixed-deposits/$fdId/close" -Headers @{ Authorization = "Bearer $customerToken"; 'Idempotency-Key' = "kafka-close-$suffix" } -TimeoutSec 20 | Out-Null
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_accounts WHERE fa_account_id=$fdId AND fa_status='CLOSED'" '1' 'customer FD closure'
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_account_events WHERE fae_account_id=$fdId AND fae_is_published=1" '5' 'published FD lifecycle events through closure'
    $history = Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/fixed-deposits/$fdId/transactions" -Headers @{ Authorization = "Bearer $customerToken" } -TimeoutSec 20
    if (-not ($history | Where-Object { $_.event_type -eq 'INTEREST_ACCRUAL' })) { throw 'FD transaction history did not include the interest accrual.' }
    Write-Output "FD booking, funding, batch accrual, maturity, closure, and transaction history verified for account $fdId."

    $earlyBooking = Invoke-JsonPost "$apiBase/api/v1/fixed-deposits" @{
        productId = $productId; savingsAccountId = $savingsId; principal = 50.00; currencyCode = 'USD'; tenureMonths = 1
    } $customerToken @{ 'Idempotency-Key' = "kafka-early-book-$suffix" }
    $earlyFdId = [long]$earlyBooking.account_id
    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/fixed-deposits/$earlyFdId/fund" -Headers @{ Authorization = "Bearer $customerToken"; 'Idempotency-Key' = "kafka-early-fund-$suffix" } -TimeoutSec 20 | Out-Null
    Invoke-DbCommand 'fd-db' 'moneypal_fd' "UPDATE fd_accounts SET fa_open_date=DATE_SUB(UTC_DATE(),INTERVAL 20 DAY) WHERE fa_account_id=$earlyFdId AND fa_status='ACTIVE'"
    $premature = Invoke-JsonPost "$apiBase/api/v1/fixed-deposits/$earlyFdId/premature-closure" @{ reason = 'Kafka demo early closure' } $customerToken @{ 'Idempotency-Key' = "kafka-early-request-$suffix" }
    if ($premature.status -ne 'PENDING' -or $premature.penalty_amount -le 0 -or $premature.net_payable_amount -le 50) { throw 'Premature closure quote did not apply the configured penalty and accrued interest.' }
    $approved = Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/admin/fixed-deposits/$earlyFdId/premature-closure/$($premature.closure_id)/approve" -Headers @{ Authorization = "Bearer $adminToken"; 'Idempotency-Key' = "kafka-early-approve-$suffix" } -TimeoutSec 20
    if ($approved.status -ne 'PREMATURELY_CLOSED' -or $approved.settledAmount -ne $premature.net_payable_amount) { throw 'Staff approval did not settle the premature closure for the quoted amount.' }
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_premature_closure WHERE fpc_closure_id=$($premature.closure_id) AND fpc_status='APPROVED' AND fpc_penalty_amount>0" '1' 'approved premature closure and applied penalty'
    Write-Output "Premature closure quote, staff approval, penalty, and settlement verified for account $earlyFdId."

    Invoke-RestMethod -Method Post -Uri "$apiBase/api/v1/fd-products/$productId/deactivate" -Headers @{ Authorization = "Bearer $adminToken" } -TimeoutSec 20 | Out-Null
    Wait-ForSql 'fd-db' 'moneypal_fd' "SELECT COUNT(*) FROM fd_product_ref WHERE fpr_product_id=$productId AND fpr_status='INACTIVE'" '1' 'product deactivation snapshot'
    $eligibleAfterDeactivation = Invoke-RestMethod -Method Get -Uri "$apiBase/api/v1/fd-products/eligible?lang=en" -Headers @{ Authorization = "Bearer $customerToken" } -TimeoutSec 20
    if (@($eligibleAfterDeactivation | Where-Object { $_.product_id -eq $productId }).Count -gt 0) { throw 'The deactivated product remained available for new FD bookings.' }
    Write-Output 'Product deactivation propagated through Kafka and removed the offer from customer eligibility.'

    $topics = & docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
    if ($LASTEXITCODE -ne 0) { throw 'Could not list Kafka topics.' }
    foreach ($topic in 'user.snapshot', 'product.catalog', 'fd.lifecycle') {
        if ($topics -notcontains $topic) { throw "Expected Kafka topic was not created: $topic" }
    }
    $topicExpectations = @{
        'user.snapshot' = 'USER_SNAPSHOT'
        'product.catalog' = 'PRODUCT_CATALOG'
        'fd.lifecycle' = 'BOOKED'
    }
    foreach ($topic in 'user.snapshot', 'product.catalog') {
        $messages = & docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic $topic --from-beginning --max-messages 1 --timeout-ms 15000
        if ($LASTEXITCODE -ne 0 -or (($messages | Out-String) -notmatch $topicExpectations[$topic])) { throw "Could not read the expected event from Kafka topic $topic." }
    }
    $offsetLine = & docker compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:9092 --topic fd.lifecycle --time -1
    if ($LASTEXITCODE -ne 0 -or $offsetLine -notmatch '^fd\.lifecycle:0:(\d+)$') { throw 'Could not read the FD lifecycle topic offset.' }
    $latestOffset = [long]$Matches[1]
    if ($latestOffset -lt 1) { throw 'FD lifecycle topic does not contain any lifecycle records.' }
    $fdMessages = & docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic fd.lifecycle --partition 0 --offset 0 --max-messages $latestOffset
    if ($LASTEXITCODE -ne 0) { throw 'Could not read FD lifecycle records from Kafka.' }
    $fdRecords = @($fdMessages | ForEach-Object { $_ | ConvertFrom-Json })
    foreach ($eventType in 'BOOKED','FUNDED','INTEREST_ACCRUED','MATURED','CLOSED') {
        if (-not ($fdRecords | Where-Object { $_.aggregateId -eq "$fdId" -and $_.eventType -eq $eventType })) { throw "Kafka record $eventType was not found for FD account $fdId." }
    }
    foreach ($eventType in 'BOOKED','FUNDED','PREMATURELY_CLOSED') {
        if (-not ($fdRecords | Where-Object { $_.aggregateId -eq "$earlyFdId" -and $_.eventType -eq $eventType })) { throw "Kafka record $eventType was not found for prematurely closed FD account $earlyFdId." }
    }
    Write-Output 'Kafka topic records verified: USER_SNAPSHOT, PRODUCT_CATALOG, normal maturity, and premature closure events.'
    Write-Output 'Kafka verification passed. The local Compose stack and its demo volumes remain running.'
}
finally {
    Pop-Location
}
