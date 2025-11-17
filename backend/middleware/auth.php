<?php
require_once __DIR__ . '/../config.php';

function verifyToken() {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';
    
    error_log("verifyToken called - Authorization header: " . ($authHeader ? "present" : "missing"));

    if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        error_log("No token provided or invalid format");
        throw new Exception('No token provided');
    }
    
    $token = $matches[1];
    error_log("Token extracted: " . substr($token, 0, 20) . "...");

    // Split token into parts
    $tokenParts = explode('.', $token);
    if (count($tokenParts) !== 3) {
        error_log("Invalid token format - parts count: " . count($tokenParts));
        throw new Exception('Invalid token format');
    }
    
    list($base64UrlHeader, $base64UrlPayload, $base64UrlSignature) = $tokenParts;
    
    // Verify signature
    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignatureCheck = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));
    
    if ($base64UrlSignature !== $base64UrlSignatureCheck) {
        error_log("Invalid token signature");
        throw new Exception('Invalid token signature');
    }
    
    // Decode payload
    $payload = json_decode(base64_decode(str_replace(['-', '_'], ['+', '/'], $base64UrlPayload)), true);
    
    if ($payload === null) {
        error_log("Failed to decode token payload");
        throw new Exception('Invalid token payload');
    }

    // Check expiration
    if (!isset($payload['exp']) || $payload['exp'] < time()) {
        error_log("Token expired - exp: " . ($payload['exp'] ?? 'not set') . ", now: " . time());
        throw new Exception('Token expired');
    }
    
    $userId = $payload['user_id'] ?? null;
    error_log("Token verified successfully - user_id: $userId");

    return $userId;
}
?>
