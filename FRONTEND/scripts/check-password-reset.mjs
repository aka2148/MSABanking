import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const component = source('../src/app/features/auth/forgot-password/forgot-password.ts');
const template = source('../src/app/features/auth/forgot-password/forgot-password.html');
const loginTemplate = source('../src/app/features/auth/login/login.html');
const messages = source('../src/app/core/i18n/customer-messages.ts');
const signup = source('../src/app/features/auth/signup/signup.ts');

assert.match(component, /type RecoveryStep = 'identifier' \| 'code' \| 'password' \| 'done'/);
assert.match(component, /setStep\('code'\)/);
assert.match(component, /setStep\('password'\)/);
assert.match(component, /setStep\('done'\)/);
assert.match(component, /cooldownSeconds = 60/);
assert.match(component, /setInterval\(/);
assert.match(component, /newPassword !== confirmPassword/);
assert.match(component, /passwordPolicyIssues\(password, this\.identifier\)/);
assert.match(component, /this\.codeForm\.reset\(\{ code: '' \}\)/);
assert.match(component, /this\.passwordForm\.reset\(\{ newPassword: '', confirmPassword: '' \}\)/);
assert.match(component, /this\.resetToken = ''/);
assert.doesNotMatch(component, /localStorage|sessionStorage|console\.|queryParams/);
assert.doesNotMatch(component, /state:\s*\{[^}]*resetToken/);
assert.match(template, /inputmode="numeric" autocomplete="one-time-code" maxlength="6"/);
assert.match(template, /autocomplete="new-password"/);
assert.match(signup, /passwordPolicyIssues\(password, username\)/);

const credentialStart = loginTemplate.indexOf('<form *ngIf="!challengeId; else otpStep"');
const credentialEnd = loginTemplate.indexOf('</form>', credentialStart);
const forgotLink = loginTemplate.indexOf('routerLink="/forgot-password"');
const otpStart = loginTemplate.indexOf('<ng-template #otpStep>');
assert.ok(credentialStart >= 0 && credentialEnd > credentialStart, 'login credentials form should exist');
assert.ok(forgotLink > credentialStart && forgotLink < credentialEnd, 'forgot link should be within credentials form');
assert.ok(otpStart > credentialEnd && forgotLink < otpStart, 'forgot link should not be within the OTP step');

const requiredMessages = [
  'Forgot password?',
  'If an account matches, we have sent a 6-digit code to its registered email.',
  'If the account is eligible, a verification code has been sent to its registered email.',
  'The code is incorrect or has expired.',
  'The reset token is invalid or has expired.',
  'Use a strong password that is different from your username.',
  'Choose a password you have not used before',
  'Too many attempts. Please wait a few minutes.',
  'Password reset is not available right now. Please try again later.',
  'Passwords don\'t match.',
];
for (const english of requiredMessages) {
  const row = messages.split(/\r?\n/).find(line => line.startsWith(`${english}|`));
  assert.ok(row, `missing translated password-reset message: ${english}`);
  assert.equal(row.split('|').length, 6, `expected six translations for: ${english}`);
}

console.log('Password-reset state, cooldown, secret handling, confirmation, login-link placement, and six-language entries passed.');
