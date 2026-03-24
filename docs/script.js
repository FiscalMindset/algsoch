// Smooth scrolling for navigation links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    });
});

// Mobile menu toggle (if we add one)
const mobileMenuToggle = () => {
    const navLinks = document.querySelector('.nav-links');
    if (navLinks.style.display === 'none') {
        navLinks.style.display = 'flex';
    } else {
        navLinks.style.display = 'none';
    }
};

// Scroll animations - add animation when elements come into view
const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
};

const observer = new IntersectionObserver(function(entries) {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.style.animation = 'fadeInUp 0.6s ease forwards';
            observer.unobserve(entry.target);
        }
    });
}, observerOptions);

// Observe all feature cards and mode cards
document.querySelectorAll('.feature-card, .mode-card, .faq-item, .tech-item').forEach(el => {
    observer.observe(el);
});

// Add animation keyframes dynamically
const style = document.createElement('style');
style.textContent = `
    @keyframes fadeInUp {
        from {
            opacity: 0;
            transform: translateY(30px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
    
    .feature-card, .mode-card, .faq-item, .tech-item {
        animation: fadeInUp 0.6s ease forwards;
        opacity: 0;
    }
`;
document.head.appendChild(style);

// Add active state to navigation based on scroll position
window.addEventListener('scroll', () => {
    const sections = document.querySelectorAll('section[id]');
    const navLinks = document.querySelectorAll('.nav-links a');
    
    let currentSection = '';
    sections.forEach(section => {
        const sectionTop = section.offsetTop;
        if (window.pageYOffset >= sectionTop - 200) {
            currentSection = section.getAttribute('id');
        }
    });
    
    navLinks.forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('href').slice(1) === currentSection) {
            link.classList.add('active');
        }
    });
});

// Add active state styling
const activeStyle = document.createElement('style');
activeStyle.textContent = `
    .nav-links a.active {
        color: var(--primary-color) !important;
        border-bottom: 2px solid var(--primary-color);
    }
`;
document.head.appendChild(activeStyle);

// Copy to clipboard functionality for mode examples
function setupCopyButtons() {
    document.querySelectorAll('.mode-details p').forEach(p => {
        p.style.cursor = 'pointer';
        p.addEventListener('mouseenter', function() {
            this.title = 'Click to copy';
            this.style.background = 'rgba(74, 144, 226, 0.1)';
            this.style.borderRadius = '4px';
            this.style.padding = '4px 8px';
        });
        
        p.addEventListener('mouseleave', function() {
            this.style.background = 'transparent';
        });
        
        p.addEventListener('click', function() {
            const text = this.innerText;
            navigator.clipboard.writeText(text).then(() => {
                const originalText = this.innerText;
                this.innerText = '✓ Copied!';
                setTimeout(() => {
                    this.innerText = originalText;
                }, 2000);
            });
        });
    });
}

// Initialize copy buttons when page loads
document.addEventListener('DOMContentLoaded', setupCopyButtons);

// Analytics tracking (optional)
function trackEvent(eventName, eventData) {
    // Send to analytics service if you add one
    console.log(`Event: ${eventName}`, eventData);
}

// Track button clicks
document.querySelectorAll('.btn').forEach(btn => {
    btn.addEventListener('click', function() {
        trackEvent('button_click', {
            text: this.innerText,
            href: this.href
        });
    });
});

// Lazy load images if any
if ('IntersectionObserver' in window) {
    const imageObserver = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const img = entry.target;
                img.src = img.dataset.src;
                img.classList.remove('lazy');
                imageObserver.unobserve(img);
            }
        });
    });

    document.querySelectorAll('img[data-src]').forEach(img => {
        imageObserver.observe(img);
    });
}

// Add loading state to download buttons
document.querySelectorAll('.btn-large[href*="download"]').forEach(btn => {
    btn.addEventListener('click', function(e) {
        const originalText = this.innerText;
        this.innerText = '⏳ Preparing download...';
        
        // Reset after 2 seconds
        setTimeout(() => {
            this.innerText = originalText;
        }, 2000);
    });
});

// Keyboard accessibility
document.addEventListener('keydown', (e) => {
    // Close modals on Escape (if you add them)
    if (e.key === 'Escape') {
        console.log('Escape pressed');
    }
    
    // Navigate links with Tab
    if (e.key === 'Tab') {
        const focused = document.activeElement;
        if (focused.tagName === 'A') {
            focused.style.outline = '2px solid var(--primary-color)';
        }
    }
});

// Set up page load animation
window.addEventListener('load', () => {
    document.body.style.opacity = '0';
    setTimeout(() => {
        document.body.style.transition = 'opacity 0.5s ease';
        document.body.style.opacity = '1';
    }, 100);
});

// Load GitHub avatar
const githubUsername = 'algsoch';
const avatarImg = document.getElementById('github-avatar');

if (avatarImg) {
    // Use GitHub's avatar URL
    avatarImg.src = `https://github.com/${githubUsername}.png`;
    avatarImg.onerror = function() {
        // Fallback if image fails to load
        this.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><circle cx="100" cy="100" r="100" fill="%23667eea"/><text x="50%" y="50%" text-anchor="middle" dy=".3em" fill="white" font-size="80" font-weight="bold">VK</text></svg>';
    };
}

// Prevent console errors in production
window.addEventListener('error', (e) => {
    console.error('Error:', e.error);
});

// Performance monitoring
window.addEventListener('load', () => {
    if (window.performance && window.performance.timing) {
        const perfData = window.performance.timing;
        const pageLoadTime = perfData.loadEventEnd - perfData.navigationStart;
        console.log('Page load time:', pageLoadTime + 'ms');
    }
});
