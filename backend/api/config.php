<?php
// Disable HTML error display
ini_set('display_errors', '0');
error_reporting(E_ALL);

// Note: Headers are set by individual API files to avoid conflicts
// Do not set headers here

define('DB_HOST', 'localhost');
define('DB_NAME', 'socially_db');
define('DB_USER', 'root');
define('DB_PASS', '');

// Only define JWT_SECRET if not already defined
if (!defined('JWT_SECRET')) {
    define('JWT_SECRET', 'your_secret_key_here_change_in_production_make_it_long_and_random');
}

function getDB() {
    try {
        $pdo = new PDO("mysql:host=" . DB_HOST . ";dbname=" . DB_NAME, DB_USER, DB_PASS);
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        $pdo->setAttribute(PDO::ATTR_EMULATE_PREPARES, false);
        return $pdo;
    } catch(PDOException $e) {
        error_log("Database connection failed: " . $e->getMessage());
        http_response_code(500);
        echo json_encode(["success" => false, "error" => "Database connection failed"]);
        exit();
    }
}

// Note: generateJWT() and verifyJWT() are defined in auth.php
// Do not duplicate them here to avoid redeclaration errors
