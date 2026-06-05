const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');

admin.initializeApp();

exports.sendChatNotification = onDocumentCreated('chats/{chatId}/messages/{messageId}', async (event) => {
  const message = event.data.data();
  const chatId = event.params.chatId;
  const senderId = message.senderId;
  if (!senderId) return;

  const chatSnap = await admin.firestore().collection('chats').doc(chatId).get();
  if (!chatSnap.exists) return;

  const chat = chatSnap.data();
  const members = Array.isArray(chat.members) ? chat.members : [];
  const targetUids = members.filter((uid) => uid !== senderId);
  if (targetUids.length === 0) return;

  const userSnaps = await Promise.all(
    targetUids.map((uid) => admin.firestore().collection('users').doc(uid).get())
  );

  const tokens = userSnaps
    .map((snap) => snap.exists ? snap.data().fcmToken : null)
    .filter((token) => typeof token === 'string' && token.length > 20);

  if (tokens.length === 0) return;

  const senderName = message.senderName || 'Window';
  let body = message.text || 'Pesan baru';
  if (message.type === 'image') body = '📷 Foto';
  if (message.type === 'video') body = '🎥 Video';

  await admin.messaging().sendEachForMulticast({
    tokens,
    data: {
      title: chat.type === 'group' ? `${chat.name || 'Grup'} - ${senderName}` : senderName,
      body,
      chatId,
      type: message.type || 'text'
    },
    android: {
      priority: 'high'
    }
  });
});
