#!/usr/bin/env node
// Disposable-gateway smoke runner. Configure BASE_URL and credentials in the
// environment; it never writes secrets or starts/stops a shared compose stack.
const base=(process.env.BASE_URL||'http://localhost').replace(/\/$/,'');
const steps=[]; const pass=(name)=>{steps.push([name,true]);console.log(`PASS ${name}`)};
const fail=(name,error)=>{steps.push([name,false]);console.log(`FAIL ${name}: ${error}`)};
async function request(name,path,options={}){try{const response=await fetch(`${base}${path}`,{...options,headers:{'Content-Type':'application/json',...(options.headers||{})}});if(!response.ok)throw new Error(`${response.status} ${await response.text()}`);return response;}catch(error){fail(name,error.message);return null;}}
const username=process.env.E2E_USERNAME,password=process.env.E2E_PASSWORD;
if(!username||!password){fail('configuration','Set E2E_USERNAME and E2E_PASSWORD for an isolated stack');process.exit(1);}
const login=await request('login','/api/v1/auth/login',{method:'POST',body:JSON.stringify({username,password})});
if(login){const body=await login.json();const token=body.token;pass('authenticated login');
  const auth={Authorization:`Bearer ${token}`};
  const products=await request('eligible FD products','/api/v1/fd-products/eligible',{headers:auth});if(products)pass('eligible FD products readable');
  const negative=await fetch(`${base}/api/v1/fixed-deposits/estimate`,{method:'POST',headers:{...auth,'Content-Type':'application/json'},body:JSON.stringify({productId:0,currencyCode:'INR',principal:0,tenureMonths:0})});
  if(negative.status>=400&&negative.status<500)pass('estimate validation returns a client error');else fail('estimate validation',`unexpected status ${negative.status}`);
  if(process.env.E2E_MPIN&&process.env.E2E_USER_ID&&process.env.E2E_KYC_CYCLE){
    const proof=await request('KYC re-apply MPIN proof','/api/v1/auth/mpin/verify',{method:'POST',headers:auth,body:JSON.stringify({mpin:process.env.E2E_MPIN,purpose:'KYC_REAPPLY',fields:{customerUserId:Number(process.env.E2E_USER_ID),cycleNumber:Number(process.env.E2E_KYC_CYCLE)}})});
    if(proof){const p=await proof.json();const reapplied=await request('KYC re-apply','/api/v1/customers/me/kyc/reapply',{method:'POST',headers:{...auth,'X-MPIN-Proof':p.proofToken},body:JSON.stringify({note:'E2E re-apply verification'})});if(reapplied)pass('KYC re-apply accepted');}
  }
  if(process.env.E2E_MPIN&&process.env.E2E_SAVINGS_ACCOUNT_ID&&process.env.E2E_WITHDRAWAL_AMOUNT){
    const accountId=Number(process.env.E2E_SAVINGS_ACCOUNT_ID), amount=String(process.env.E2E_WITHDRAWAL_AMOUNT);
    const proof=await request('withdrawal MPIN proof','/api/v1/auth/mpin/verify',{method:'POST',headers:auth,body:JSON.stringify({mpin:process.env.E2E_MPIN,purpose:'WITHDRAWAL',fields:{accountId,amount}})});
    if(proof){const p=await proof.json();const key=`e2e-withdrawal-${Date.now()}`;const withdrawal=await request('customer savings withdrawal','/api/v1/savings-accounts/withdrawals',{method:'POST',headers:{...auth,'Idempotency-Key':key,'X-MPIN-Proof':p.proofToken},body:JSON.stringify({fromAccountId:accountId,amount:Number(amount),remark:'isolated e2e smoke'})});if(withdrawal)pass('customer savings withdrawal accepted');}
  }
}
if(steps.some(([,ok])=>!ok))process.exitCode=1;
