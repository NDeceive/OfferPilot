# Sets the resume-only DeepSeek API key for this Windows user without echoing it.
$enteredKey = Read-Host 'Enter DeepSeek API Key (input is hidden)' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($enteredKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) { throw 'API Key cannot be empty.' }
    [Environment]::SetEnvironmentVariable('DEEPSEEK_RESUME_API_KEY', $plainKey.Trim(), 'User')
    Write-Host 'Saved for the current Windows user. Restart the OfferPilot backend to apply it.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    Remove-Variable plainKey, enteredKey -ErrorAction SilentlyContinue
}
