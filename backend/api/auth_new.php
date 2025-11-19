<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

require_once __DIR__ . '/../config.php';

function generateJWT($userId) {
    $header = json_encode(['typ' => 'JWT', 'alg' => 'HS256']);
    $payload = json_encode([
        'user_id' => $userId,
        'exp' => time() + (7 * 24 * 60 * 60)
    ]);

    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($header));
    $base64UrlPayload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($payload));

    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    return $base64UrlHeader . "." . $base64UrlPayload . "." . $base64UrlSignature;
}

// Initialize database
try {
    $db = getDB();
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Database connection failed']);
    exit();
}

$method = $_SERVER['REQUEST_METHOD'];
$action = $_GET['action'] ?? '';

// SIGNUP
if ($method === 'POST' && $action === 'signup') {
    try {
        $input = file_get_contents('php://input');
        $data = json_decode($input, true);

        if (empty($data['email']) || empty($data['password'])) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Email and password required']);
            exit();
        }

        // Check existing email
        $stmt = $db->prepare("SELECT id FROM users WHERE email = :email");
        $stmt->execute([':email' => $data['email']]);
        $result = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($result) {
            http_response_code(409);
            echo json_encode(['success' => false, 'error' => 'Email already registered']);
            exit();
        }

        // Create user
        $userId = uniqid('user_', true);
        $passwordHash = password_hash($data['password'], PASSWORD_BCRYPT);
        $username = $data['username'] ?? explode('@', $data['email'])[0];
        $displayName = trim(($data['firstName'] ?? '') . ' ' . ($data['lastName'] ?? ''));

        $stmt = $db->prepare("INSERT INTO users (id, email, password_hash, username, first_name, last_name, display_name, dob, profile_pic_url) VALUES (:id, :email, :password_hash, :username, :first_name, :last_name, :display_name, :dob, :profile_pic_url)");
        $stmt->execute([
            ':id' => $userId,
            ':email' => $data['email'],
            ':password_hash' => $passwordHash,
            ':username' => $username,
            ':first_name' => $data['firstName'] ?? '',
            ':last_name' => $data['lastName'] ?? '',
            ':display_name' => $displayName,
            ':dob' => $data['dob'] ?? '',
            ':profile_pic_url' => $data['profilePicUrl'] ?? ''
        ]);

        $token = generateJWT($userId);

        echo json_encode([
            'success' => true,
            'token' => $token,
            'userId' => $userId,
            'username' => $username
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Signup failed: ' . $e->getMessage()]);
        exit();
    }
}

// LOGIN
if ($method === 'POST' && $action === 'login') {
    try {
        $input = file_get_contents('php://input');
        $data = json_decode($input, true);

        if (empty($data['email']) || empty($data['password'])) {
            http_response_code(400);
            echo json_encode(['success' => false, 'error' => 'Email and password required']);
            exit();
        }

        $stmt = $db->prepare("SELECT id, password_hash, username FROM users WHERE email = :email");
        $stmt->execute([':email' => $data['email']]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$user || !password_verify($data['password'], $user['password_hash'])) {
            http_response_code(401);
            echo json_encode(['success' => false, 'error' => 'Invalid credentials']);
            exit();
        }

        $token = generateJWT($user['id']);

        echo json_encode([
            'success' => true,
            'token' => $token,
            'userId' => $user['id'],
            'username' => $user['username']
        ]);
        exit();

    } catch (Exception $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Login failed: ' . $e->getMessage()]);
        exit();
    }
}

// TEST
if ($action === 'test') {
    echo json_encode(['success' => true, 'message' => 'Backend working', 'timestamp' => time()]);
    exit();
}

// Invalid request
http_response_code(400);
echo json_encode(['success' => false, 'error' => 'Invalid request']);
exit();

