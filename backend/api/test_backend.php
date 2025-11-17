<?php
// Simple test script to diagnose backend issues
error_reporting(E_ALL);
ini_set('display_errors', 1);

echo "<h1>Backend Diagnostic Test</h1>";

// Test 1: PHP Version
echo "<h2>1. PHP Version</h2>";
echo "PHP Version: " . phpversion() . "<br>";

// Test 2: Database Connection
echo "<h2>2. Database Connection</h2>";
$db = new mysqli('localhost', 'root', '', 'socially_db');
if ($db->connect_error) {
    echo "❌ FAILED: " . $db->connect_error . "<br>";
} else {
    echo "✅ SUCCESS: Connected to database<br>";
    echo "Database: socially_db<br>";
}

// Test 3: Check if users table exists
echo "<h2>3. Users Table Check</h2>";
$result = $db->query("SHOW TABLES LIKE 'users'");
if ($result->num_rows > 0) {
    echo "✅ SUCCESS: users table exists<br>";

    // Check table structure
    $columns = $db->query("DESCRIBE users");
    echo "<h3>Table Structure:</h3>";
    echo "<pre>";
    while ($col = $columns->fetch_assoc()) {
        echo $col['Field'] . " (" . $col['Type'] . ") - " . ($col['Null'] == 'YES' ? 'NULL' : 'NOT NULL') . "\n";
    }
    echo "</pre>";
} else {
    echo "❌ FAILED: users table does not exist<br>";
}

// Test 4: Check JWT_SECRET
echo "<h2>4. JWT_SECRET Check</h2>";
define('JWT_SECRET', 'your_secret_key_change_in_production_2024_socially');
if (defined('JWT_SECRET')) {
    echo "✅ SUCCESS: JWT_SECRET is defined<br>";
    echo "Value: " . JWT_SECRET . "<br>";
} else {
    echo "❌ FAILED: JWT_SECRET is not defined<br>";
}

// Test 5: Test JSON encoding
echo "<h2>5. JSON Encoding Test</h2>";
$testData = ['success' => true, 'message' => 'Test message', 'userId' => 'test_123'];
$json = json_encode($testData);
if ($json) {
    echo "✅ SUCCESS: JSON encoding works<br>";
    echo "Sample: " . $json . "<br>";
} else {
    echo "❌ FAILED: JSON encoding failed<br>";
}

// Test 6: Test password hashing
echo "<h2>6. Password Hashing Test</h2>";
$testPassword = 'TestPassword123!';
$hash = password_hash($testPassword, PASSWORD_BCRYPT);
if ($hash && password_verify($testPassword, $hash)) {
    echo "✅ SUCCESS: Password hashing and verification works<br>";
} else {
    echo "❌ FAILED: Password hashing failed<br>";
}

// Test 7: Check auth.php file
echo "<h2>7. Auth.php File Check</h2>";
if (file_exists(__DIR__ . '/auth.php')) {
    echo "✅ SUCCESS: auth.php exists in current directory<br>";
    echo "Path: " . __DIR__ . '/auth.php<br>';
} else {
    echo "❌ FAILED: auth.php not found in current directory<br>";
}

// Test 8: Check config.php file
echo "<h2>8. Config.php File Check</h2>";
if (file_exists(__DIR__ . '/config.php')) {
    echo "✅ SUCCESS: config.php exists in current directory<br>";
    echo "Path: " . __DIR__ . '/config.php<br>';
} else {
    echo "❌ FAILED: config.php not found in current directory<br>";
}

// Test 9: Test a simple signup simulation
echo "<h2>9. Signup Simulation Test</h2>";
$testEmail = 'test_' . time() . '@example.com';
$testPassword = password_hash('TestPass123!', PASSWORD_BCRYPT);
$testUserId = uniqid('user_', true);
$testUsername = 'testuser_' . time();

$stmt = $db->prepare("INSERT INTO users (id, email, password_hash, username, first_name, last_name, display_name, dob, profile_pic_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
if ($stmt) {
    $firstName = 'Test';
    $lastName = 'User';
    $displayName = 'Test User';
    $dob = '01-01-2000';
    $profilePic = '';

    $stmt->bind_param("sssssssss", $testUserId, $testEmail, $testPassword, $testUsername, $firstName, $lastName, $displayName, $dob, $profilePic);

    if ($stmt->execute()) {
        echo "✅ SUCCESS: Test user inserted successfully<br>";
        echo "User ID: " . $testUserId . "<br>";
        echo "Email: " . $testEmail . "<br>";

        // Clean up - delete test user
        $db->query("DELETE FROM users WHERE id = '$testUserId'");
        echo "Test user cleaned up.<br>";
    } else {
        echo "❌ FAILED: Could not insert test user<br>";
        echo "Error: " . $stmt->error . "<br>";
    }
} else {
    echo "❌ FAILED: Could not prepare INSERT statement<br>";
    echo "Error: " . $db->error . "<br>";
}

echo "<hr>";
echo "<h2>Summary</h2>";
echo "If all tests pass, your backend should work correctly.<br>";
echo "Copy auth.php and config.php from this directory to C:\\xampp\\htdocs\\backend\\api\\<br>";
?>

