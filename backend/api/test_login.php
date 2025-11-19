<?php
// Simple test file to verify login endpoint returns valid JSON
header('Content-Type: text/plain');

$url = 'http://192.168.18.55/backend/api/auth.php?action=login';
$data = json_encode([
    'email' => 'daniyalkhawar41@gmail.com',
    'password' => 'Root@pass1'
]);

$ch = curl_init($url);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, $data);
curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

echo "HTTP Code: $httpCode\n";
echo "Response Length: " . strlen($response) . "\n";
echo "First 10 chars (hex): " . bin2hex(substr($response, 0, 10)) . "\n";
echo "Response:\n";
echo $response;
echo "\n\nJSON Valid: " . (json_decode($response) !== null ? 'YES' : 'NO') . "\n";

if (json_decode($response) === null) {
    echo "JSON Error: " . json_last_error_msg() . "\n";
}

