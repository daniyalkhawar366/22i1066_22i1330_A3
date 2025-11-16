<?php
require_once __DIR__ . '/../config.php';

function verifyToken() {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';
    
    if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'No token provided']);
        exit;
    }
    
    $token = $matches[1];
    
    // Split token into parts
    $tokenParts = explode('.', $token);
    if (count($tokenParts) !== 3) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Invalid token format']);
        exit;
    }
    
    list($base64UrlHeader, $base64UrlPayload, $base64UrlSignature) = $tokenParts;
    
    // Verify signature
    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignatureCheck = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));
    
    if ($base64UrlSignature !== $base64UrlSignatureCheck) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Invalid token signature']);
        exit;
    }
    
    // Decode payload
    $payload = json_decode(base64_decode(str_replace(['-', '_'], ['+', '/'], $base64UrlPayload)), true);
    
    // Check expiration
    if (!isset($payload['exp']) || $payload['exp'] < time()) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Token expired']);
        exit;
    }
    
    return $payload['user_id'] ?? null;
}
?>
