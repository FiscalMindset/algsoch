# Troubleshooting & Common Issues

## Problem: "Chat messages not saving"

**Solution:**
1. Ensure app has **Storage Permission** on Oppo A77s
   - Go to: Settings → Apps → Algsoch → Permissions → Storage
   - Enable "Files and media"

2. Check if the app is being forced to close
   - Settings → Apps → Algsoch → Battery → Unrestricted

## Problem: "Can't see history button on older Android"

**Solution:**
- Update to Android 26+ (your Oppo A77s supports Android 26+)
- Try rotating the screen to landscape for more space
- History button is the clock icon (🕐) in the top bar

## Problem: "Export not working / No files created"

**Solution:**
1. Verify storage permissions again
2. Check available storage space on device
3. Try exporting in JSON format first (smaller file)
4. Files are saved to: `/data/data/com.runanywhere.kotlin_starter_example/files/chat_history/`

To access exported files:
- Use File Manager app → Storage → Files
- Look for "chat_history" folder
- Or connect to PC and browse via USB

## Problem: "Messages disappear after restart"

**Solution:**
1. Make sure you've sent at least one message (triggers first save)
2. Wait 2-3 seconds after message before closing app
3. Check that app isn't being killed by system
   - Settings → Apps → Algsoch → Force Stop (toggle off)

## Problem: "Can't load previous chats"

**Solution:**
1. Tap History button (🕐)
2. Wait for sessions to load (may take 1-2 seconds)
3. If still empty, try:
   - Close and reopen the app
   - Send a new message (this triggers loading)

## Problem: "Export status not showing"

**Solution:**
- Status messages appear for 5 seconds, then disappear
- Export happens in background, check files after 2 seconds
- Try exporting again if first attempt fails

---

## For Developers: Debug Mode

To check if persistence is working:

```kotlin
// In Android Studio, open Logcat and filter by: "Algsoch"

// Check these paths to verify files are created:
// Device File Explorer → data/data/com.runanywhere.kotlin_starter_example/files/chat_history/

// Manual check in code:
val chatDir = chatHistoryManager.getChatExportDirectory()
Log.d("Algsoch", "Chat files stored at: $chatDir")
```

## Performance Tips

1. **For large chats (500+ messages)**
   - Export as JSON (more efficient)
   - Consider deleting old sessions periodically

2. **For slow devices**
   - Let auto-save complete (indicated by brief UI pause)
   - Don't export while model is loading

3. **For low storage**
   - Export important chats regularly
   - Delete old sessions you don't need

---

## Network Limitations (Important!)

⚠️ **For Sikkim/Gangtok Network:**

Since you mentioned difficult network conditions:
- Chat persistence works **entirely offline** ✅
- No internet needed for saving/loading chats ✅
- Export files can be transferred via USB later ✅
- Custom modes work offline ✅

**Recommendation:**
1. Set up custom modes while on better network
2. Use the app offline in Gangtok
3. Export chats when back to good network (via USB)

---

## Getting Help

If you still have issues:

1. **Clear App Cache** (WARNING: clears chat history!)
   - Settings → Apps → Algsoch → Storage → Clear Cache

2. **Check Permissions** on Oppo A77s:
   - Settings → Apps → Permissions → Files and Media
   - Grant to Algsoch

3. **Reinstall App** (last resort):
   - Backup by exporting chats first!
   - Uninstall and reinstall app

---

## Quick Checklist

- [ ] Storage permission granted
- [ ] Sent at least one message
- [ ] Waited 2+ seconds before closing app
- [ ] History button visible in top bar
- [ ] Can see exported files in chat_history folder
- [ ] Status message appeared after export

If all checked ✅ - Your persistence features are working!

