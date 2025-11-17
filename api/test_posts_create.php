<?php
// Test script to verify posts.php create endpoint
error_reporting(E_ALL);
ini_set('display_errors', 1);

echo "Testing posts.php create endpoint...\n\n";

// Simulate the request
$_SERVER['REQUEST_METHOD'] = 'POST';
$_GET['action'] = 'create';

// Create a test JWT token (you'll need to replace this with a real token from your app)
$testToken = 'Bearer YOUR_JWT_TOKEN_HERE';
$_SERVER['HTTP_AUTHORIZATION'] = $testToken;

// Simulate POST data
$testData = [
    'postId' => 'test_' . time(),
    'caption' => 'Test post',
    'imageUrls' => [
        'http://192.168.18.55/backend/uploads/test.jpg'
    ],
    'timestamp' => time() * 1000
];

// Set the input stream
$GLOBALS['test_input'] = json_encode($testData);

// Override file_get_contents for testing
function file_get_contents_override($filename) {
    if ($filename === 'php://input') {
        return $GLOBALS['test_input'];
    }
    return file_get_contents($filename);
}

echo "Test data: " . json_encode($testData, JSON_PRETTY_PRINT) . "\n\n";
echo "Response from posts.php:\n";
echo "------------------------\n";

// Capture output
ob_start();
include 'posts.php';
$output = ob_get_clean();

echo $output . "\n";
echo "------------------------\n\n";

// Validate JSON
$decoded = json_decode($output, true);
if ($decoded === null) {
    echo "ERROR: Response is not valid JSON!\n";
    echo "Raw output: " . var_export($output, true) . "\n";
} else {
    echo "SUCCESS: Response is valid JSON\n";
    echo "Decoded: " . json_encode($decoded, JSON_PRETTY_PRINT) . "\n";
}
?>

