# Quick Start Guide: Chat Persistence Features

## 🎯 What's New?

Your Algsoch app now **saves every chat message automatically**! 

✅ Messages persist across app restarts  
✅ Access previous conversations anytime  
✅ Export chats as CSV or JSON  
✅ App learns your preferences  

---

## 📱 Using on Your Oppo A77s

### First Time Setup
1. Open the app normally
2. Send your first message
3. App automatically saves it in the background
4. **That's it!** Everything else is automatic

### Viewing Previous Chats
1. Look at the **top bar** of Algsoch screen
2. Tap the **clock icon** (🕐) on the right
3. See all your previous conversations
4. Tap any to continue that conversation
5. Long press to delete old ones

### Exporting Your Data
1. Tap the **download icon** (⬇️) next to history button
2. Choose format:
   - **CSV** = Use in Excel / Google Sheets
   - **JSON** = For developers / backup
3. Files saved to your phone automatically
4. Message confirms: "Chat exported to..."

### Finding Your Exports
1. Open **Files** app on your phone
2. Go to **Storage** or **Files**
3. Look for folder named **chat_history**
4. Your CSV/JSON files are there

---

## 💾 Where Data is Saved

**Physical Location:**
```
Phone Storage → Internal Storage → 
  Android/data/com.runanywhere.kotlin_starter_example/files/
  └── chat_history/
      ├── Previous chats (JSON)
      └── Exported files (CSV/JSON)
```

**How Much Space?**
- Each message ≈ 200-500 bytes
- 100 messages ≈ 50 KB
- 1000 messages ≈ 500 KB

---

## ⚡ Important for Offline Use (Gangtok)

**Great news!** Everything works 100% offline:
- ✅ Chat persistence = No internet needed
- ✅ Session loading = No internet needed  
- ✅ Data export = Happens on your phone

**Recommended approach:**
1. Set up custom modes on good network (if needed)
2. Use app fully offline in Gangtok
3. Export important chats when you get good network
4. Backup exported files via USB

---

## 🎨 Understanding the UI

### Top Bar Changes

```
[Back] [Algsoch Title] [History] [Export] [Language] [Level]
                         🕐        ⬇️
```

**New buttons explained:**
- 🕐 **History** = View/load previous chats
- ⬇️ **Export** = Download chat as file

### History Menu

```
┌─────────────────────────┐
│  Chat Sessions          │
├─────────────────────────┤
│ [+ New Session]         │
│                         │
│ Previous Sessions:      │
│ ┌─────────────────────┐ │
│ │ chat_17110... │ ❌  │ │
│ │ 25 messages   │     │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ chat_17110... │ ❌  │ │
│ │ 42 messages   │     │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

Click a session to load it  
Click ❌ to delete it

### Export Menu

```
┌─────────────────────────┐
│  Export Chat            │
├─────────────────────────┤
│ Choose export format:   │
│                         │
│ [📄 Export as CSV]      │
│ [{}  Export as JSON]    │
│                         │
│ Status: "Exported to..." │
│                         │
│         [Close]         │
└─────────────────────────┘
```

Click CSV for spreadsheets  
Click JSON for backup

---

## ❓ FAQ

### Q: Do I need internet for this to work?
**A:** No! Everything is 100% offline. Perfect for Gangtok.

### Q: How often are messages saved?
**A:** Automatically after every message exchange. No action needed.

### Q: Can I lose my chats?
**A:** Only if you force-close the app before it saves (~1-2 seconds). Otherwise no.

### Q: Where do exported files go?
**A:** Inside your phone storage, in a folder called `chat_history`. Use Files app to find them.

### Q: Can I delete chats?
**A:** Yes! From the History menu, click the ❌ icon on any chat.

### Q: What's the difference between CSV and JSON?
- **CSV** = Good for Excel, Google Sheets, analysis
- **JSON** = Good for developers, programming, backup

Choose CSV for normal use.

### Q: How much storage does this use?
**A:** Very little. 1000 messages ≈ 500KB.

---

## ⚙️ Settings & Permissions

### Required Permissions
The app already has what it needs:
- ✅ Storage access (needed for chats)
- ✅ File read/write (happens automatically)

**Nothing extra to set up!**

### To Enable (if permission denied)
1. Open **Settings**
2. Go to **Apps**
3. Find **Algsoch**
4. Tap **Permissions**
5. Allow **"Files and media"**
6. Go back to app

---

## 🆘 Troubleshooting

### "I don't see the history button"
- Rotate phone to landscape (more space)
- Look at right side of top bar for 🕐 icon

### "History is empty"
- App needs at least one message to create history
- Send a message first
- Wait 1-2 seconds
- Then open history

### "Export isn't working"
- Check storage permission (Settings → Apps → Permissions)
- Try exporting as CSV first
- Files appear in `chat_history` folder after 2 seconds

### "My old chats disappeared"
- They're still saved! Tap History button
- They load in the background (may take 1-2 seconds)
- If still missing, reinstall app (but this deletes chats!)

### "App is slow when exporting"
- This is normal, export happens in background
- Don't close app during export
- Large chats take longer (500+ messages)

---

## 🔒 Privacy

**Your data:**
- ✅ Stays on YOUR phone
- ✅ Never sent anywhere (unless you export to cloud)
- ✅ You can delete anytime
- ✅ Backed up by exporting to USB

---

## 📊 What Gets Saved

### Per message, we save:
- Your question/message
- AI response
- When it was sent (timestamp)
- Whether you liked/disliked it

### Your preferences, we track:
- Languages you use
- Response modes you prefer
- Difficulty level you pick
- Your like/dislike history

This helps the app learn **what YOU like!** 🧠

---

## 🚀 Pro Tips

1. **Regular Backups** - Export important chats monthly to USB
2. **Named Sessions** - Send a message with session topic first
3. **Clean Up** - Delete old test chats to save space
4. **Language Learning** - Switch languages, see your preferences
5. **Offline Mode** - You're ready for Gangtok! ✈️

---

## 📞 Need Help?

Check these documents in your project:
- `IMPLEMENTATION_COMPLETE_PERSISTENCE.md` - Full technical details
- `PERSISTENCE_FEATURES.md` - Feature documentation  
- `TROUBLESHOOTING.md` - Detailed troubleshooting

---

**Ready to go!** 🎉

Your chats are now permanently saved. Enjoy using Algsoch with peace of mind!

