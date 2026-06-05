(function () {
    var themeKey = 'financePortalLoginTheme';
    var langKey = 'financePortalLoginLang';

    function getLang() {
        try {
            var urlLang = new URL(window.location.href).searchParams.get('kc_locale');
            if (urlLang === 'tr' || urlLang === 'en') {
                localStorage.setItem(langKey, urlLang);
                return urlLang;
            }
        } catch (e) {}

        var active = document.querySelector('.fp-locale-active, a[hreflang].fp-locale-active');
        var activeCode = active ? (active.getAttribute('lang') || active.getAttribute('hreflang') || active.textContent || '').trim().slice(0, 2).toLowerCase() : '';
        if (activeCode === 'tr' || activeCode === 'en') return activeCode;

        try {
            var saved = localStorage.getItem(langKey);
            if (saved === 'tr' || saved === 'en') return saved;
        } catch (e) {}

        var htmlLang = (document.documentElement.lang || '').split('-')[0].toLowerCase();
        return htmlLang === 'en' ? 'en' : 'tr';
    }

    function withLocaleUrl(lang) {
        try {
            var url = new URL(window.location.href);
            url.searchParams.set('kc_locale', lang);
            return url.toString();
        } catch (e) {
            return '?kc_locale=' + lang;
        }
    }

    function applyTheme(theme) {
        var root = document.documentElement;
        var toggle = document.getElementById('fp-theme-toggle');
        root.classList.toggle('fp-theme-dark', theme === 'dark');
        root.classList.toggle('fp-theme-light', theme === 'light');

        if (!toggle) return;
        var lang = getLang();
        var lightLabel = lang === 'en' ? 'Light' : 'Açık';
        var darkLabel = lang === 'en' ? 'Dark' : 'Koyu';
        var isSystemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        var isDark = theme === 'dark' || (!theme && isSystemDark);
        var label = toggle.querySelector('.fp-theme-toggle-text');
        if (label) label.textContent = isDark ? darkLabel : lightLabel;
        toggle.setAttribute('aria-pressed', isDark ? 'true' : 'false');
        toggle.setAttribute('aria-label', lang === 'en' ? 'Switch theme' : 'Temayı değiştir');
    }

    function ensureTopControls() {
        var lang = getLang();
        document.documentElement.lang = lang;

        var localeNav = document.querySelector('.fp-locale-nav');
        if (!localeNav) {
            localeNav = document.createElement('nav');
            localeNav.className = 'fp-locale-nav';
            localeNav.setAttribute('aria-label', lang === 'en' ? 'Language selector' : 'Dil seçici');
            localeNav.innerHTML = '<a class="fp-locale-btn" lang="tr">TR</a><a class="fp-locale-btn" lang="en">EN</a>';
            document.body.appendChild(localeNav);
        } else if (localeNav.parentNode !== document.body) {
            document.body.appendChild(localeNav);
        }

        localeNav.querySelectorAll('.fp-locale-btn').forEach(function (btn) {
            var code = (btn.getAttribute('lang') || btn.getAttribute('hreflang') || btn.textContent || '').trim().slice(0, 2).toLowerCase();
            if (code !== 'tr' && code !== 'en') return;
            btn.href = withLocaleUrl(code);
            btn.classList.toggle('fp-locale-active', code === lang);
            btn.addEventListener('click', function () {
                try { localStorage.setItem(langKey, code); } catch (e) {}
            });
        });

        var toggle = document.getElementById('fp-theme-toggle');
        if (!toggle) {
            toggle = document.createElement('button');
            toggle.id = 'fp-theme-toggle';
            toggle.className = 'fp-theme-toggle';
            toggle.type = 'button';
            toggle.innerHTML = '<span class="fp-theme-toggle-dot" aria-hidden="true"></span><span class="fp-theme-toggle-text"></span>';
            document.body.appendChild(toggle);
        } else if (toggle.parentNode !== document.body) {
            document.body.appendChild(toggle);
        }

        if (!toggle.dataset.fpBound) {
            toggle.dataset.fpBound = 'true';
            toggle.addEventListener('click', function () {
                var nextTheme = document.documentElement.classList.contains('fp-theme-dark') ? 'light' : 'dark';
                try { localStorage.setItem(themeKey, nextTheme); } catch (e) {}
                applyTheme(nextTheme);
            });
        }

        var savedTheme = null;
        try { savedTheme = localStorage.getItem(themeKey); } catch (e) {}
        if (savedTheme !== 'dark' && savedTheme !== 'light') savedTheme = null;
        applyTheme(savedTheme);
    }

    function markSecondaryPages() {
        var hasShowcase = !!document.querySelector('.fp-login-showcase');
        var hasOtp = !!document.getElementById('kc-otp-login-form') || !!document.getElementById('otp');
        if (!hasShowcase || hasOtp) {
            document.body.classList.add('fp-secondary-login-page');
        }
    }

    function init() {
        markSecondaryPages();
        ensureTopControls();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();