<?php
ob_start();
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/../middleware/auth.php';
ob_clean();

header('Content-Type: application/json');

try {
    $db = getDB();
    $currentUserId = verifyToken();
    
    if (!$currentUserId) {
        echo json_encode(['success' => false, 'error' => 'Unauthorized']);
        exit;
    }

    $action = $_GET['action'] ?? '';

    if ($action === 'getChatList') {
        $stmt = $db->prepare("
            SELECT 
                m.chat_id,
                m.sender_id,
                m.receiver_id,
                m.message_text,
                m.message_type,
                m.created_at,
                CASE 
                    WHEN m.sender_id = ? THEN u2.user_id
                    ELSE u1.user_id
                END as other_user_id,
                CASE 
                    WHEN m.sender_id = ? THEN u2.username
                    ELSE u1.username
                END as other_username,
                CASE 
                    WHEN m.sender_id = ? THEN u2.profile_pic_url
                    ELSE u1.profile_pic_url
                END as other_profile_pic
            FROM messages m
            INNER JOIN (
                SELECT chat_id, MAX(created_at) as max_time
                FROM messages
                WHERE sender_id = ? OR receiver_id = ?
                GROUP BY chat_id
            ) latest ON m.chat_id = latest.chat_id AND m.created_at = latest.max_time
            LEFT JOIN users u1 ON m.sender_id = u1.user_id
            LEFT JOIN users u2 ON m.receiver_id = u2.user_id
            WHERE m.sender_id = ? OR m.receiver_id = ?
            ORDER BY m.created_at DESC
        ");
        
        $stmt->bind_param('sssssss', $currentUserId, $currentUserId, $currentUserId, $currentUserId, $currentUserId, $currentUserId, $currentUserId);
        $stmt->execute();
        $result = $stmt->get_result();
        
        $chats = [];
        while ($row = $result->fetch_assoc()) {
            $chats[] = [
                'chatId' => $row['chat_id'],
                'otherUserId' => $row['other_user_id'],
                'otherUsername' => $row['other_username'] ?? '',
                'otherProfilePic' => $row['other_profile_pic'],
                'lastMessage' => $row['message_text'] ?? '',
                'lastMessageType' => $row['message_type'] ?? 'text',
                'lastTimestamp' => strtotime($row['created_at']) * 1000,
                'unreadCount' => 0
            ];
        }
        
        $stmt->close();
        echo json_encode(['success' => true, 'chats' => $chats]);
        exit;
    }

    if ($action === 'getMessages') {
        $chatId = $_GET['chat_id'] ?? '';
        $limit = (int)($_GET['limit'] ?? 50);
        
        if (!$chatId) {
            echo json_encode(['success' => false, 'error' => 'chat_id required']);
            exit;
        }
        
        $stmt = $db->prepare("
            SELECT message_id, sender_id, receiver_id, message_text, message_type, 
                   image_urls, created_at, is_delivered, is_read
            FROM messages
            WHERE chat_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        ");
        
        $stmt->bind_param('si', $chatId, $limit);
        $stmt->execute();
        $result = $stmt->get_result();
        
        $messages = [];
        while ($row = $result->fetch_assoc()) {
            $imageUrls = $row['image_urls'] ? json_decode($row['image_urls'], true) : [];
            
            $messages[] = [
                'id' => $row['message_id'],
                'senderId' => $row['sender_id'],
                'text' => $row['message_text'] ?? '',
                'type' => $row['message_type'] ?? 'text',
                'imageUrls' => is_array($imageUrls) ? $imageUrls : [],
                'timestamp' => strtotime($row['created_at']) * 1000,
                'delivered' => (bool)$row['is_delivered'],
                'read' => (bool)$row['is_read']
            ];
        }
        
        $messages = array_reverse($messages);
        $stmt->close();
        
        echo json_encode(['success' => true, 'messages' => $messages]);
        exit;
    }

    if ($action === 'send') {
        $input = json_decode(file_get_contents('php://input'), true);
        
        $receiverId = $input['receiverId'] ?? '';
        $text = $input['text'] ?? '';
        $imageUrls = $input['imageUrls'] ?? [];
        
        if (!$receiverId) {
            echo json_encode(['success' => false, 'error' => 'receiverId required']);
            exit;
        }
        
        $chatId = ($currentUserId < $receiverId) 
            ? "{$currentUserId}_{$receiverId}" 
            : "{$receiverId}_{$currentUserId}";
        
        $messageId = 'msg_' . uniqid() . '_' . time();
        $messageType = !empty($imageUrls) ? 'image' : 'text';
        $imageUrlsJson = json_encode($imageUrls);
        
        $stmt = $db->prepare("
            INSERT INTO messages (message_id, chat_id, sender_id, receiver_id, message_text, message_type, image_urls, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        ");
        
        $stmt->bind_param('sssssss', $messageId, $chatId, $currentUserId, $receiverId, $text, $messageType, $imageUrlsJson);
        $stmt->execute();
        $stmt->close();
        
        echo json_encode([
            'success' => true,
            'message' => [
                'id' => $messageId,
                'senderId' => $currentUserId,
                'text' => $text,
                'type' => $messageType,
                'imageUrls' => $imageUrls,
                'timestamp' => time() * 1000,
                'delivered' => false,
                'read' => false
            ]
        ]);
        exit;
    }

    echo json_encode(['success' => false, 'error' => 'Invalid action']);
    exit;

} catch (Exception $e) {
    error_log("Messages error: " . $e->getMessage());
    echo json_encode(['success' => false, 'error' => 'Server error']);
    exit;
}
