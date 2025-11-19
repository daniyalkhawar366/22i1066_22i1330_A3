<?php
ob_start();
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/jwt_helper.php';
ob_clean();

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Authorization, Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

try {
    $db = getDB();

    // Get and verify JWT token
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? '';
    $token = str_replace('Bearer ', '', $authHeader);
    $userData = verifyJWT($token);

    if (!$userData) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit();
    }

    $currentUserId = $userData['user_id'];
    $action = $_GET['action'] ?? '';

    // GET CHAT LIST
    if ($action === 'getChatList') {
        try {
            error_log("getChatList called for user: " . $currentUserId);

            // Get all distinct chat_ids for this user
            $stmt = $db->prepare("
                SELECT DISTINCT chat_id
                FROM messages
                WHERE chat_id LIKE CONCAT('%', :userId, '%')
                ORDER BY chat_id
            ");
            $stmt->execute([':userId' => $currentUserId]);
            $chatIds = $stmt->fetchAll(PDO::FETCH_COLUMN);

            error_log("Found " . count($chatIds) . " chat_ids for user: " . $currentUserId);

            $chats = [];
            foreach ($chatIds as $chatId) {
                // Get latest message for this chat
                $msgStmt = $db->prepare("
                    SELECT chat_id, sender_id, text, type, timestamp, image_urls
                    FROM messages
                    WHERE chat_id = :chatId
                    ORDER BY timestamp DESC
                    LIMIT 1
                ");
                $msgStmt->execute([':chatId' => $chatId]);
                $row = $msgStmt->fetch(PDO::FETCH_ASSOC);

                if (!$row) {
                    error_log("No messages found for chat: $chatId");
                    continue;
                }

                // Determine other user ID from chat_id
                // chat_id format: user_xxx_user_yyy
                // Need to split by middle underscore, not all underscores

                // Method 1: Find the middle point
                $firstUserEnd = strpos($chatId, '_user_', 5); // Start search after first "user_"
                if ($firstUserEnd === false) {
                    error_log("Invalid chat_id format: $chatId (no middle '_user_' separator found)");
                    continue;
                }

                $userId1 = substr($chatId, 0, $firstUserEnd);
                $userId2 = substr($chatId, $firstUserEnd + 1); // Skip the underscore

                error_log("Parsed chat_id: $chatId -> userId1=$userId1, userId2=$userId2");

                $otherUserId = ($userId1 === $currentUserId) ? $userId2 : $userId1;

                error_log("Chat $chatId: other user is $otherUserId");

                // Get other user info
                $userStmt = $db->prepare("SELECT id, username, profile_pic_url, last_seen FROM users WHERE id = :userId");
                $userStmt->execute([':userId' => $otherUserId]);
                $otherUser = $userStmt->fetch(PDO::FETCH_ASSOC);

                if (!$otherUser) {
                    error_log("Skipping chat - user not found: $otherUserId");
                    continue;
                }

                // Calculate if user is online (active within last 5 minutes)
                $lastSeen = $otherUser['last_seen'] ?? null;
                $isOnline = false;
                if ($lastSeen) {
                    $lastSeenTime = strtotime($lastSeen);
                    $currentTime = time();
                    $isOnline = ($currentTime - $lastSeenTime) < 300; // 5 minutes
                }

                // Format last message preview
                $lastMessageText = $row['text'] ?? '';
                $messageType = $row['type'] ?? 'text';
                $senderId = $row['sender_id'] ?? '';

                // For image messages, show "Image" or "You: Image"
                if ($messageType === 'image' || (!empty($row['image_urls']) && $row['image_urls'] !== '[]')) {
                    if ($senderId === $currentUserId) {
                        $lastMessageText = 'You: Image';
                    } else {
                        $lastMessageText = 'Image';
                    }
                } elseif ($messageType === 'call') {
                    // Keep call messages as is
                    $lastMessageText = $row['text'] ?? '';
                } else {
                    // For text messages, add "You: " prefix if sent by current user
                    if ($senderId === $currentUserId && !empty($lastMessageText)) {
                        $lastMessageText = 'You: ' . $lastMessageText;
                    }
                }

                $chats[] = [
                    'chatId' => $chatId,
                    'otherUserId' => $otherUser['id'],
                    'otherUsername' => $otherUser['username'] ?? '',
                    'otherProfilePic' => $otherUser['profile_pic_url'] ?? '',
                    'lastMessage' => $lastMessageText,
                    'lastMessageType' => $messageType,
                    'lastTimestamp' => (int)$row['timestamp'],
                    'unreadCount' => 0,
                    'isOnline' => $isOnline
                ];
            }

            error_log("getChatList returning " . count($chats) . " chats");
            echo json_encode(['success' => true, 'chats' => $chats]);
            exit;
        } catch (Exception $e) {
            error_log("getChatList error: " . $e->getMessage());
            // Return empty chats on error instead of crashing
            echo json_encode(['success' => true, 'chats' => []]);
            exit;
        }
    }

    // GET MESSAGES
    if ($action === 'getMessages') {
        try {
            $chatId = $_GET['chat_id'] ?? '';
            $limit = (int)($_GET['limit'] ?? 50);

            if (!$chatId) {
                echo json_encode(['success' => false, 'error' => 'chat_id required']);
                exit;
            }

            error_log("getMessages called for chat: $chatId, limit: $limit, user: $currentUserId");

            $stmt = $db->prepare("
                SELECT id, sender_id, text, type, image_urls, timestamp, delivered, read_status
                FROM messages
                WHERE chat_id = :chatId
                ORDER BY timestamp ASC
                LIMIT :limit
            ");

            $stmt->bindValue(':chatId', $chatId, PDO::PARAM_STR);
            $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
            $stmt->execute();

            $messages = [];
            while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                $imageUrls = $row['image_urls'] ? json_decode($row['image_urls'], true) : [];

                $messages[] = [
                    'id' => $row['id'],
                    'senderId' => $row['sender_id'],
                    'text' => $row['text'] ?? '',
                    'type' => $row['type'] ?? 'text',
                    'imageUrls' => is_array($imageUrls) ? $imageUrls : [],
                    'timestamp' => (int)$row['timestamp'],
                    'delivered' => (bool)$row['delivered'],
                    'read' => (bool)$row['read_status']
                ];
            }

            error_log("getMessages returning " . count($messages) . " messages");
            echo json_encode(['success' => true, 'messages' => $messages]);
            exit;
        } catch (Exception $e) {
            error_log("getMessages error: " . $e->getMessage());
            echo json_encode(['success' => false, 'error' => 'Database error: ' . $e->getMessage()]);
            exit;
        }
    }

    // SEND MESSAGE
    if ($action === 'send') {
        try {
            $input = json_decode(file_get_contents('php://input'), true);

            $receiverId = $input['receiverId'] ?? '';
            $text = $input['text'] ?? '';
            $imageUrls = $input['imageUrls'] ?? [];

            if (!$receiverId) {
                echo json_encode(['success' => false, 'error' => 'receiverId required']);
                exit;
            }

            // Determine chat_id (smaller user ID first for consistency)
            $chatId = ($currentUserId < $receiverId)
                ? "{$currentUserId}_{$receiverId}"
                : "{$receiverId}_{$currentUserId}";

            // Create or update chat entry in chats table (if it exists)
            // First check if chats table exists
            $tableCheck = $db->query("SHOW TABLES LIKE 'chats'");
            if ($tableCheck && $tableCheck->rowCount() > 0) {
                // Chats table exists, insert or update
                $chatStmt = $db->prepare("
                    INSERT INTO chats (id, user_a_id, user_b_id, created_at, updated_at)
                    VALUES (:chatId, :userA, :userB, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE updated_at = NOW()
                ");

                $userA = $currentUserId < $receiverId ? $currentUserId : $receiverId;
                $userB = $currentUserId < $receiverId ? $receiverId : $currentUserId;

                $chatStmt->execute([
                    ':chatId' => $chatId,
                    ':userA' => $userA,
                    ':userB' => $userB
                ]);
            }

            $messageId = 'msg_' . uniqid() . '_' . time();
            $messageType = !empty($imageUrls) ? 'image' : 'text';
            $imageUrlsJson = json_encode($imageUrls);
            $timestamp = time() * 1000; // Milliseconds

            $stmt = $db->prepare("
                INSERT INTO messages (id, chat_id, sender_id, text, type, image_urls, timestamp, delivered, read_status)
                VALUES (:id, :chatId, :senderId, :text, :type, :imageUrls, :timestamp, 0, 0)
            ");

            $stmt->execute([
                ':id' => $messageId,
                ':chatId' => $chatId,
                ':senderId' => $currentUserId,
                ':text' => $text,
                ':type' => $messageType,
                ':imageUrls' => $imageUrlsJson,
                ':timestamp' => $timestamp
            ]);

            echo json_encode([
                'success' => true,
                'message' => [
                    'id' => $messageId,
                    'senderId' => $currentUserId,
                    'text' => $text,
                    'type' => $messageType,
                    'imageUrls' => $imageUrls,
                    'timestamp' => $timestamp,
                    'delivered' => false,
                    'read' => false
                ]
            ]);
            exit;
        } catch (Exception $e) {
            error_log("Send message error: " . $e->getMessage());
            echo json_encode(['success' => false, 'error' => 'Failed to send message: ' . $e->getMessage()]);
            exit;
        }
    }

    // EDIT MESSAGE
    if ($action === 'edit') {
        $input = json_decode(file_get_contents('php://input'), true);

        $messageId = $input['messageId'] ?? '';
        $newText = $input['text'] ?? '';

        if (!$messageId || !$newText) {
            echo json_encode(['success' => false, 'error' => 'messageId and text required']);
            exit;
        }

        // Verify message belongs to current user and is within 5 minutes
        $checkStmt = $db->prepare("SELECT sender_id, timestamp FROM messages WHERE id = :id");
        $checkStmt->execute([':id' => $messageId]);
        $message = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if (!$message || $message['sender_id'] !== $currentUserId) {
            http_response_code(403);
            echo json_encode(['success' => false, 'error' => 'Not authorized']);
            exit;
        }

        $now = time() * 1000;
        if ($now - (int)$message['timestamp'] > 5 * 60 * 1000) {
            echo json_encode(['success' => false, 'error' => 'Edit allowed only within 5 minutes of sending']);
            exit;
        }

        $stmt = $db->prepare("UPDATE messages SET text = :text WHERE id = :id");
        $stmt->execute([
            ':text' => $newText,
            ':id' => $messageId
        ]);

        echo json_encode(['success' => true, 'message' => 'Message updated']);
        exit;
    }

    // DELETE MESSAGE
    if ($action === 'delete') {
        $input = json_decode(file_get_contents('php://input'), true);

        $messageId = $input['messageId'] ?? '';

        if (!$messageId) {
            echo json_encode(['success' => false, 'error' => 'messageId required']);
            exit;
        }

        // Verify message belongs to current user and is within 5 minutes
        $checkStmt = $db->prepare("SELECT sender_id, image_urls, timestamp FROM messages WHERE id = :id");
        $checkStmt->execute([':id' => $messageId]);
        $message = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if (!$message || $message['sender_id'] !== $currentUserId) {
            http_response_code(403);
            echo json_encode(['success' => false, 'error' => 'Not authorized']);
            exit;
        }

        $now = time() * 1000;
        if ($now - (int)$message['timestamp'] > 5 * 60 * 1000) {
            echo json_encode(['success' => false, 'error' => 'Delete allowed only within 5 minutes of sending']);
            exit;
        }

        // Delete associated images from server
        if ($message['image_urls']) {
            $imageUrls = json_decode($message['image_urls'], true);
            if (is_array($imageUrls)) {
                foreach ($imageUrls as $url) {
                    $filename = basename($url);
                    $filepath = __DIR__ . '/../uploads/' . $filename;
                    if (file_exists($filepath)) {
                        unlink($filepath);
                    }
                }
            }
        }

        $stmt = $db->prepare("DELETE FROM messages WHERE id = :id");
        $stmt->execute([':id' => $messageId]);

        echo json_encode(['success' => true, 'message' => 'Message deleted']);
        exit;
    }

    // UPLOAD IMAGE FOR MESSAGE
    if ($action === 'uploadImage') {
        if (!isset($_FILES['image'])) {
            echo json_encode(['success' => false, 'error' => 'No image uploaded']);
            exit;
        }

        $file = $_FILES['image'];

        error_log("Image upload - Name: {$file['name']}, Type: {$file['type']}, Size: {$file['size']}");

        if ($file['error'] !== UPLOAD_ERR_OK) {
            echo json_encode(['success' => false, 'error' => 'Upload error code: ' . $file['error']]);
            exit;
        }

        // Check file extension instead of MIME type (more reliable)
        $extension = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
        $allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp'];

        if (!in_array($extension, $allowedExtensions)) {
            error_log("Invalid file extension: $extension");
            echo json_encode(['success' => false, 'error' => 'Invalid file type. Allowed: jpg, jpeg, png, gif, webp']);
            exit;
        }

        if ($file['size'] > 10 * 1024 * 1024) { // 10MB
            echo json_encode(['success' => false, 'error' => 'File too large. Max 10MB']);
            exit;
        }

        $uploadDir = __DIR__ . '/../uploads/';
        if (!is_dir($uploadDir)) {
            mkdir($uploadDir, 0777, true);
        }

        $filename = 'msg_' . $currentUserId . '_' . time() . '_' . uniqid() . '.' . $extension;
        $destination = $uploadDir . $filename;

        error_log("Saving image to: $destination");

        if (move_uploaded_file($file['tmp_name'], $destination)) {
            $fileUrl = "http://192.168.18.55/backend/uploads/" . $filename;
            error_log("Image uploaded successfully: $fileUrl");
            echo json_encode([
                'success' => true,
                'url' => $fileUrl
            ]);
        } else {
            error_log("Failed to move uploaded file from {$file['tmp_name']} to $destination");
            echo json_encode(['success' => false, 'error' => 'Failed to move uploaded file']);
        }
        exit;
    }

    // UPDATE USER ACTIVITY (for online status)
    if ($action === 'updateActivity') {
        try {
            $stmt = $db->prepare("UPDATE users SET last_seen = NOW() WHERE id = :userId");
            $stmt->execute([':userId' => $currentUserId]);
            echo json_encode(['success' => true]);
            exit;
        } catch (Exception $e) {
            error_log("Update activity error: " . $e->getMessage());
            echo json_encode(['success' => false, 'error' => 'Failed to update activity']);
            exit;
        }
    }

    echo json_encode(['success' => false, 'error' => 'Invalid action']);
    exit;

} catch (Exception $e) {
    error_log("Messages error: " . $e->getMessage());
    echo json_encode(['success' => false, 'error' => 'Server error: ' . $e->getMessage()]);
    exit;
}
