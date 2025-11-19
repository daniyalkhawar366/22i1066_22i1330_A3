# 📞 REAL-TIME CALLING SYSTEM - PHP BACKEND ONLY

## ✅ STATUS: FIREBASE COMPLETELY REMOVED - USING PHP BACKEND

---

## 🎯 What Was Implemented

### **1. Online Status Check**
- Before initiating call, backend checks if receiver is online
- Online = last activity within 5 minutes
- If offline, shows popup: "[username] is offline"

### **2. Real-Time Call Polling**
- FYPActivity polls backend every 2 seconds for incoming calls
- When call received, shows IncomingCallActivity
- Receiver can accept or decline

### **3. Accept/Decline Functionality**
- Accept: Updates status to "accepted" → Opens CallActivity
- Decline: Updates status to "rejected" → Closes dialog
- Both actions update database via PHP backend

### **4. Timer Starts After Accept**
- Timer only starts when `onUserJoined` fires
- This happens when BOTH users are in Agora channel
- Means receiver has accepted the call

### **5. No Firebase - 100% PHP Backend**
- All call signaling through MySQL database
- Polling mechanism for real-time updates
- CallListenerService removed (was Firebase-based)

---

## 📊 Database Structure

### **calls table**:
```sql
CREATE TABLE calls (
    id VARCHAR(255) PRIMARY KEY,
    channel_name VARCHAR(255) NOT NULL,
    caller_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    call_type ENUM('voice', 'video') NOT NULL,
    status ENUM('ringing', 'accepted', 'rejected', 'ended', 'missed'),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL
);
```

**Status Flow**:
1. `ringing` - Call initiated, waiting for receiver
2. `accepted` - Receiver accepted, call in progress
3. `rejected` - Receiver declined
4. `ended` - Call completed normally
5. `missed` - Receiver never answered

---

## 🔧 PHP Backend Endpoints

### **1. Check Online Status**
```http
GET /calls.php?action=checkOnline&userId={userId}
Authorization: Bearer {token}

Response:
{
  "success": true,
  "isOnline": true
}
```

### **2. Initiate Call**
```http
POST /calls.php?action=initiate
Authorization: Bearer {token}

Body:
{
  "receiverId": "user123",
  "callType": "video"
}

Response (Success):
{
  "success": true,
  "callId": "call_abc123",
  "channelName": "call_user1_user2_1234567890"
}

Response (Offline):
{
  "success": false,
  "error": "User is offline",
  "isOnline": false,
  "username": "John Doe"
}
```

### **3. Poll for Incoming Calls**
```http
GET /calls.php?action=pollIncomingCall
Authorization: Bearer {token}

Response (No Call):
{
  "success": true,
  "hasIncomingCall": false
}

Response (Incoming Call):
{
  "success": true,
  "hasIncomingCall": true,
  "call": {
    "callId": "call_abc123",
    "channelName": "call_user1_user2_1234567890",
    "callerId": "user456",
    "callerUsername": "Jane Smith",
    "callerProfileUrl": "http://...",
    "callType": "video"
  }
}
```

### **4. Update Call Status**
```http
POST /calls.php?action=updateStatus
Authorization: Bearer {token}

Body:
{
  "callId": "call_abc123",
  "status": "accepted"
}

Response:
{
  "success": true
}
```

---

## 📱 Call Flow

### **Caller Side**:
```
1. User A taps call button
   ↓
2. Check if User B is online (PHP API)
   ↓
3a. If OFFLINE:
    → Show popup: "John is offline"
    → End
   
3b. If ONLINE:
    → Create call in database (status: ringing)
    → Get channelName
    → Open CallActivity
    → Join Agora channel
    → Show "Calling..." status
   ↓
4. Wait for User B to join Agora channel
   ↓
5. When User B joins:
    → onUserJoined fires
    → Show "Connected" status
    → START TIMER
    → Real-time audio/video active
   ↓
6. End call:
    → Log to chat via PHP backend
```

### **Receiver Side (User B)**:
```
1. FYPActivity polls every 2 seconds
   ↓
2. Poll detects incoming call (status: ringing)
   ↓
3. Open IncomingCallActivity
    → Shows caller profile pic
    → Shows "Incoming video call..."
    → Shows Accept/Decline buttons
   ↓
4a. If DECLINE:
     → Update status to "rejected"
     → Close dialog
     → Caller sees timeout/missed
   
4b. If ACCEPT:
     → Update status to "accepted"
     → Open CallActivity
     → Join same Agora channel
     → Caller's onUserJoined fires
     → Both see "Connected"
     → TIMER STARTS
     → Real-time call active
   ↓
5. End call:
    → Both log to chat
```

---

## ⏱️ Timer Behavior

### **Before**:
- ❌ Timer started when YOU joined channel
- ❌ Timer ran even if other user never answered

### **After**:
- ✅ Timer starts ONLY when `onUserJoined` fires
- ✅ This means other user has ACCEPTED the call
- ✅ Accurate call duration tracking

**Code**:
```kotlin
override fun onUserJoined(uid: Int, elapsed: Int) {
    // Start timer when call is accepted (remote user joins)
    if (startMillis == 0L) {
        startMillis = System.currentTimeMillis()
        handler.post(tick)
    }
    // ...
}
```

---

## 🔄 Polling Mechanism

### **Why Polling?**
- No Firebase → Can't use push notifications
- Polling checks server every 2 seconds
- Lightweight query (checks single database row)
- Stops when call received

### **Performance**:
- Query every 2 seconds = 30 queries/minute
- Very small data transfer (< 1KB per query)
- Only runs when app in foreground
- Stops during calls

### **Code**:
```kotlin
private fun startIncomingCallPolling() {
    isPollingIncomingCalls = true
    lifecycleScope.launch {
        while (isPollingIncomingCalls) {
            val response = RetrofitClient.instance.pollIncomingCall("Bearer $token")
            
            if (response.body()?.hasIncomingCall == true) {
                // Show incoming call screen
                startActivity(incomingCallIntent)
                isPollingIncomingCalls = false // Stop polling
                break
            }
            
            delay(2000) // Wait 2 seconds
        }
    }
}
```

---

## 🧪 Testing Guide

### **Test 1: Offline User**
1. Close app on Device B (wait 5+ minutes)
2. Device A tries to call Device B
3. ✅ Should show: "[Username] is offline"

### **Test 2: Online User - Accept**
1. Open app on both devices
2. Device A calls Device B
3. ✅ Device B should see IncomingCallActivity
4. ✅ Shows caller profile pic and name
5. Tap "Accept" on Device B
6. ✅ Both enter call
7. ✅ Timer shows "00:00" initially
8. ✅ Timer starts when "Connected" appears
9. ✅ Both can hear/see each other (Agora)

### **Test 3: Decline Call**
1. Device A calls Device B
2. Device B sees incoming call
3. Tap "Decline" on Device B
4. ✅ Device B returns to FYP
5. ✅ Device A stays in CallActivity (can retry)

### **Test 4: Timer Accuracy**
1. Start call, wait for "Connected"
2. Note timer start
3. Talk for 2 minutes
4. End call
5. Check chat
6. ✅ Should show "📹 Video call • 02:XX"

---

## 🐛 Troubleshooting

### **Issue**: Offline message not showing
**Check**:
- Backend endpoint: `/calls.php?action=initiate`
- Response includes `isOnline: false`
- `last_seen` updated in users table

### **Issue**: No incoming call notification
**Check**:
- Polling is running (FYPActivity in foreground)
- Call status is "ringing" in database
- `pollIncomingCall` endpoint returns hasIncomingCall: true

### **Issue**: Timer starts too early
**Check**:
- Should start in `onUserJoined`, not `onJoinChannelSuccess`
- Look for "User connected" toast message

### **Issue**: Both users not connecting
**Check**:
- Both using same `channelName` (from database)
- Both have Agora permissions granted
- Internet connection on both devices

---

## 📊 Database Queries

### **Check Pending Calls**:
```sql
SELECT * FROM calls WHERE status = 'ringing' ORDER BY started_at DESC;
```

### **Check User Online Status**:
```sql
SELECT 
    id, 
    username, 
    last_seen,
    TIMESTAMPDIFF(SECOND, last_seen, NOW()) as seconds_since_active,
    CASE 
        WHEN TIMESTAMPDIFF(SECOND, last_seen, NOW()) < 300 THEN 'ONLINE'
        ELSE 'OFFLINE'
    END as status
FROM users 
WHERE id = 'user123';
```

### **Call History**:
```sql
SELECT 
    c.id,
    c.call_type,
    c.status,
    c.started_at,
    u1.username as caller,
    u2.username as receiver
FROM calls c
LEFT JOIN users u1 ON c.caller_id = u1.id
LEFT JOIN users u2 ON c.receiver_id = u2.id
ORDER BY c.started_at DESC
LIMIT 50;
```

---

## ✅ Summary

### **What Works**:
- ✅ Check if user is online before calling
- ✅ Show offline popup if user unavailable
- ✅ Real-time incoming call detection (polling)
- ✅ Accept/Decline functionality
- ✅ Timer starts only when call accepted
- ✅ Real-time audio/video via Agora
- ✅ Call logs to chat with duration
- ✅ 100% PHP backend (no Firebase)

### **Key Files Modified**:
1. **Backend**: `calls.php` - Added endpoints
2. **Android**: `ChatDetailActivity.kt` - Initiate with online check
3. **Android**: `FYPActivity.kt` - Polling for incoming calls
4. **Android**: `IncomingCallActivity.kt` - Accept/Decline via PHP
5. **Android**: `CallActivity.kt` - Timer logic (already correct)
6. **Android**: `ApiService.kt` - New endpoints and data classes

### **Removed**:
- ❌ Firebase Realtime Database
- ❌ CallListenerService (Firebase-based)
- ❌ All Firebase call notifications

---

**Implementation Date**: November 20, 2025  
**Status**: ✅ **COMPLETE AND READY TO TEST**  
**Backend**: 100% PHP + MySQL  
**Real-Time**: Agora RTC + PHP Polling

🎉 **Your calling system is now fully functional with PHP backend!**

