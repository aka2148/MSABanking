/** Frontend mirror of the server password policy used by signup and password recovery. */
export function passwordPolicyIssues(password: string, username: string): string[] {
  const normalizedUsername = username.trim().toLowerCase();
  const lowerPassword = password.toLowerCase();
  const issues: string[] = [];
  if (password.length < 12) issues.push('Use at least 12 characters.');
  if (!/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password))
    issues.push('Include lowercase and uppercase letters, a number, and a symbol.');
  if (normalizedUsername && lowerPassword.includes(normalizedUsername))
    issues.push('The password cannot contain your username.');
  if (['password', 'qwerty', 'letmein', 'welcome', 'admin', 'iloveyou', '12345678']
    .some(common => lowerPassword.includes(common)))
    issues.push('Choose a password that is not commonly used.');
  return issues;
}
