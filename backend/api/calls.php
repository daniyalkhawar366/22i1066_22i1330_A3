<?php
ob_start();
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/jwt_helper.php';
ob_clean();

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, OPTIONS');
header('Access-Control-Allow-Headers: Authorization, Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

try {
    $db = getDB();
    
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

    // CHECK IF USER IS ONLINE
    if ($action === 'checkOnline') {
        $userId = $_GET['userId'] ?? '';

        if (!$userId) {
            echo json_encode(['success' => false, 'error' => 'userId required']);
            exit;
        }

        $stmt = $db->prepare("SELECT last_seen FROM users WHERE id = :userId");
        $stmt->execute([':userId' => $userId]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($user) {
            $lastSeen = $user['last_seen'];
            $isOnline = false;

            if ($lastSeen) {
                $lastSeenTime = strtotime($lastSeen);
                $currentTime = time();
                $isOnline = ($currentTime - $lastSeenTime) < 300; // 5 minutes
            }

            echo json_encode(['success' => true, 'isOnline' => $isOnline]);
        } else {
            echo json_encode(['success' => false, 'error' => 'User not found']);
        }
        exit;
    }

    // POLL FOR INCOMING CALLS
    if ($action === 'pollIncomingCall') {
        // Only show calls started in last 30 seconds
        $stmt = $db->prepare("
            SELECT c.id, c.channel_name, c.caller_id, c.receiver_id, c.call_type, c.started_at,
                   u.username as caller_username, u.profile_pic_url as caller_profile_url
            FROM calls c
            LEFT JOIN users u ON c.caller_id = u.id
            WHERE c.receiver_id = :userId
            AND c.status = 'ringing'
            AND c.started_at >= (NOW() - INTERVAL 30 SECOND)
            ORDER BY c.started_at DESC
            LIMIT 1
        ");
        $stmt->execute([':userId' => $currentUserId]);
        $call = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($call) {
            echo json_encode([
                'success' => true,
                'hasIncomingCall' => true,
                'call' => [
                    'callId' => $call['id'],
                    'channelName' => $call['channel_name'],
                    'callerId' => $call['caller_id'],
                    'callerUsername' => $call['caller_username'],
                    'callerProfileUrl' => $call['caller_profile_url'],
                    'callType' => $call['call_type']
                ]
            ]);
        } else {
            echo json_encode(['success' => true, 'hasIncomingCall' => false]);
        }
        exit;
    }

    // INITIATE CALL
    if ($action === 'initiate') {
        $input = json_decode(file_get_contents('php://input'), true);
        $receiverId = $input['receiverId'] ?? '';
        $callType = $input['callType'] ?? 'voice'; // voice or video
        
        if (!$receiverId) {
            echo json_encode(['success' => false, 'error' => 'receiverId required']);
            exit;
        }

        // Check if receiver is online
        $stmt = $db->prepare("SELECT last_seen, username FROM users WHERE id = :userId");
        $stmt->execute([':userId' => $receiverId]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$user) {
            echo json_encode(['success' => false, 'error' => 'User not found']);
            exit;
        }

        $isOnline = false;
        if ($user['last_seen']) {
            $lastSeenTime = strtotime($user['last_seen']);
            $currentTime = time();
            $isOnline = ($currentTime - $lastSeenTime) < 300; // 5 minutes
        }

        if (!$isOnline) {
            echo json_encode([
                'success' => false,
                'error' => 'User is offline',
                'isOnline' => false,
                'username' => $user['username']
            ]);
            exit;
        }

        // Use callId as channel name (short, simple, and same for both users)
        $callId = 'call_' . uniqid();
        $channelName = $callId; // Short channel name

        // Insert call record
        $stmt = $db->prepare("
            INSERT INTO calls (id, channel_name, caller_id, receiver_id, call_type, status, started_at)
            VALUES (:id, :channel, :caller, :receiver, :type, 'ringing', NOW())
        ");
        $stmt->execute([
            ':id' => $callId,
            ':channel' => $channelName,
            ':caller' => $currentUserId,
            ':receiver' => $receiverId,
            ':type' => $callType
        ]);

        echo json_encode([
            'success' => true,
            'callId' => $callId,
            'channelName' => $channelName
        ]);
        exit;
    }

    // UPDATE CALL STATUS
    if ($action === 'updateStatus') {
        $input = json_decode(file_get_contents('php://input'), true);
        $callId = $input['callId'] ?? '';
        $status = $input['status'] ?? ''; // accepted, rejected, ended, missed
        
        $stmt = $db->prepare("UPDATE calls SET status = :status WHERE id = :id");
        $stmt->execute([':status' => $status, ':id' => $callId]);

        // If call ended, add to chat as message
        if ($status === 'ended' || $status === 'rejected' || $status === 'missed') {
            $callStmt = $db->prepare("SELECT caller_id, receiver_id, call_type, started_at FROM calls WHERE id = :id");
            $callStmt->execute([':id' => $callId]);
            $call = $callStmt->fetch(PDO::FETCH_ASSOC);
            
            if ($call) {
                $chatId = ($call['caller_id'] < $call['receiver_id'])
                    ? "{$call['caller_id']}_{$call['receiver_id']}"
                    : "{$call['receiver_id']}_{$call['caller_id']}";
                
                $callText = $status === 'ended' ? 
                    "{$call['call_type']} call ended" : 
                    "{$call['call_type']} call {$status}";
                
                $messageId = 'msg_' . uniqid() . '_' . time();
                $timestamp = time() * 1000;
                
                $msgStmt = $db->prepare("
                    INSERT INTO messages (id, chat_id, sender_id, text, type, timestamp, delivered, read_status)
                    VALUES (:id, :chatId, :senderId, :text, 'call', :timestamp, 0, 0)
                ");
                $msgStmt->execute([
                    ':id' => $messageId,
                    ':chatId' => $chatId,
                    ':senderId' => $call['caller_id'],
                    ':text' => $callText,
                    ':timestamp' => $timestamp
                ]);
            }
        }

        echo json_encode(['success' => true]);
        exit;
    }

    // GET USER INFO FOR CALL
    if ($action === 'getUserInfo') {
        $userId = $_GET['userId'] ?? '';
        
        $stmt = $db->prepare("SELECT id, username, profile_pic_url FROM users WHERE id = :userId");
        $stmt->execute([':userId' => $userId]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if ($user) {
            echo json_encode(['success' => true, 'user' => $user]);
        } else {
            echo json_encode(['success' => false, 'error' => 'User not found']);
        }
        exit;
    }

    // LOG CALL TO CHAT
    if ($action === 'logCall') {
        $input = json_decode(file_get_contents('php://input'), true);
        $receiverId = $input['receiverId'] ?? '';
        $callType = $input['callType'] ?? 'voice';
        $duration = $input['duration'] ?? 0; // in seconds

        if (!$receiverId) {
            echo json_encode(['success' => false, 'error' => 'receiverId required']);
            exit;
        }

        // Create chat ID
        $chatId = ($currentUserId < $receiverId)
            ? "{$currentUserId}_{$receiverId}"
            : "{$receiverId}_{$currentUserId}";

        // Format duration
        $minutes = floor($duration / 60);
        $seconds = $duration % 60;
        $durationStr = sprintf("%02d:%02d", $minutes, $seconds);

        // Create message text
        $callTypeEmoji = ($callType === 'video') ? '📹' : '📞';
        $callTypeName = ($callType === 'video') ? 'Video' : 'Voice';
        $messageText = "{$callTypeEmoji} {$callTypeName} call • {$durationStr}";

        // Insert message
        $messageId = 'msg_' . uniqid() . '_' . time();
        $timestamp = time() * 1000;

        $msgStmt = $db->prepare("
            INSERT INTO messages (id, chat_id, sender_id, text, type, timestamp, delivered, read_status)
            VALUES (:id, :chatId, :senderId, :text, 'call', :timestamp, 0, 0)
        ");
        $msgStmt->execute([
            ':id' => $messageId,
            ':chatId' => $chatId,
            ':senderId' => $currentUserId,
            ':text' => $messageText,
            ':timestamp' => $timestamp
        ]);

        // Update chat last message (if chats table exists)
        try {
            $updateChatStmt = $db->prepare("
                UPDATE chats
                SET last_message = :message, last_message_time = :time
                WHERE chat_id = :chatId
            ");
            $updateChatStmt->execute([
                ':message' => $messageText,
                ':time' => $timestamp,
                ':chatId' => $chatId
            ]);
        } catch (Exception $e) {
            // Chats table might not exist, ignore
        }

        echo json_encode(['success' => true]);
        exit;
    }

    // GET CALL STATUS
    if ($action === 'getCallStatus') {
        $callId = $_GET['callId'] ?? '';
        $stmt = $db->prepare("SELECT status FROM calls WHERE id = :id");
        $stmt->execute([':id' => $callId]);
        $call = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($call) {
            echo json_encode(['success' => true, 'status' => $call['status']]);
        } else {
            echo json_encode(['success' => false, 'error' => 'Call not found']);
        }
        exit;
    }

    echo json_encode(['success' => false, 'error' => 'Invalid action']);

} catch (Exception $e) {
    error_log("Calls error: " . $e->getMessage());
    echo json_encode(['success' => false, 'error' => 'Server error: ' . $e->getMessage()]);
}

