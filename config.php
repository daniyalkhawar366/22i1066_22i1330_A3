<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, GET, PUT, DELETE");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

define('DB_HOST', 'localhost');
define('DB_NAME', 'socially_db');
define('DB_USER', 'root');
define('DB_PASS', '');

function getDB() {
    try {
        $pdo = new PDO("mysql:host=" . DB_HOST . ";dbname=" . DB_NAME, DB_USER, DB_PASS);
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        return $pdo;
    } catch(PDOException $e) {
        http_response_code(500);
        echo json_encode(["error" => "Database connection failed"]);
        exit();
    }
}

function generateJWT($userId) {
    $payload = [
        'user_id' => $userId,
        'exp' => time() + (7 * 24 * 60 * 60)
    ];
    return base64_encode(json_encode($payload));
}

function verifyJWT($token) {
    $decoded = json_decode(base64_decode($token), true);
    if ($decoded && $decoded['exp'] > time()) {
        return $decoded['user_id'];
    }
    return null;
}
?>
