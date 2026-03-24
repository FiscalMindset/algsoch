# GitHub Pages Deployment Guide for Algsoch

## Quick Start

Your Algsoch GitHub Pages website is now ready to deploy! Follow these steps:

### Step 1: Enable GitHub Pages

1. Go to your GitHub repository
2. Click **Settings** → **Pages**
3. Under "Source", select branch: **main** (or your default branch)
4. Select folder: **/docs**
5. Click **Save**

Your site will be published at: `https://yourusername.github.io/algsoch`

### Step 2: Update Repository Settings

Make sure these files are in the `/docs` folder:
- `index.html` - Main website
- `style.css` - Styling
- `script.js` - Interactivity

### Step 3: Customize Your Website

Edit `docs/index.html` and update:
- Replace download link: `href="#"` → Your APK download URL
- Replace GitHub repo link: Update all `href="https://github.com"` links
- Add your contact email
- Update descriptions if needed

### Step 4: Deploy APK

1. Upload your app's APK file to GitHub Releases
2. Update the download button to point to it:
   ```html
   <a href="https://github.com/yourusername/algsoch/releases/download/v1.0/algsoch.apk" 
      class="btn btn-primary btn-large">📥 Download APK</a>
   ```

## Features of the Website

✅ **7 Mode Documentation** - All modes with descriptions and examples
✅ **App Features** - Complete feature showcase
✅ **Download Section** - Easy APK download with installation guide
✅ **FAQ** - Common questions answered
✅ **Technical Details** - AI models and tech stack info
✅ **Responsive Design** - Works on mobile, tablet, and desktop
✅ **Smooth Animations** - Professional look and feel
✅ **SEO Optimized** - Better visibility in search results

## Customization Tips

### Colors
Edit `:root` variables in `style.css`:
```css
:root {
    --primary-color: #4a90e2;      /* Change blue*/
    --secondary-color: #50c878;     /* Change green */
    --dark-bg: #1a1a2e;             /* Change dark background */
}
```

### Fonts
Change font-family in `body` selector in `style.css`

### Content
- Update mode descriptions in `index.html`
- Add/remove features in the features section
- Update FAQ items
- Change footer text

## Adding Features

### Feature Card
```html
<div class="feature-card">
    <div class="feature-icon">🎯</div>
    <h3>Feature Name</h3>
    <p>Feature description here</p>
</div>
```

### FAQ Item
```html
<div class="faq-item">
    <h4>Question?</h4>
    <p>Answer to the question</p>
</div>
```

## Troubleshooting

### Site not showing?
- Wait 2-3 minutes after enabling
- Check that files are in `/docs` folder
- Verify branch and folder settings in GitHub Pages

### Styling not loading?
- Clear browser cache (Ctrl+Shift+Delete)
- Check that `style.css` is in `/docs` folder
- Verify CSS file path is correct

### JavaScript not working?
- Check browser console for errors (F12)
- Verify `script.js` is in `/docs` folder
- Check that script tag points to correct file

## SEO Optimization

Add to `<head>` for better search visibility:
```html
<meta name="description" content="Algsoch - AI Study Companion for smarter learning">
<meta name="keywords" content="ai, study, learning, tutor, education">
<meta name="author" content="Your Name">
```

## Analytics (Optional)

Add Google Analytics by including in `<head>`:
```html
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
<script>
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', 'GA_MEASUREMENT_ID');
</script>
```

## Share Your Website

- **Twitter**: "Check out Algsoch - AI Study Companion: [your-link]"
- **LinkedIn**: Post about your new learning app
- **Reddit**: Share in education subreddits
- **Discord**: Share in study/programming servers

## Support

Need help? Create an issue in your GitHub repository!

---

**Created with ❤️ for students everywhere**
