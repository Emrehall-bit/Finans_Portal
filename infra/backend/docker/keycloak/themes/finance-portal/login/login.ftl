<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('username','password')
    displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??;
    section>

    <#-- ═══════════════════════════════════════════════════════════
         HEADER SECTION — renders inside .card-pf > header
         Contains: language switcher (fixed), logo, title, slogan
         ═══════════════════════════════════════════════════════════ -->
    <#if section = "header">

        <#assign currentLang = "tr">
        <#if locale?? && locale.current??>
            <#assign currentLang = locale.current?split("-")[0]>
        </#if>

        <div class="fp-login-showcase" aria-hidden="true">
            <section class="fp-showcase-panel fp-showcase-left">
                <div class="fp-panel-head">
                    <span class="fp-panel-icon">◔</span>
                    <div>
                        <strong><#if currentLang == 'en'>PORTFOLIO MANAGEMENT<#else>PORTFÖY YÖNETİMİ</#if></strong>
                        <p><#if currentLang == 'en'>Track assets and improve performance from one screen.<#else>Varlıklarınızı tek ekrandan yönetin, performansınızı artırın.</#if></p>
                    </div>
                </div>
                <div class="fp-feature-row"><span>↗</span><div><b><#if currentLang == 'en'>Portfolio Tracking<#else>Portföy Takibi</#if></b><p><#if currentLang == 'en'>Monitor total value and changes instantly.<#else>Tüm varlıklarınızı anlık olarak takip edin.</#if></p></div></div>
                <div class="fp-feature-row"><span>●</span><div><b><#if currentLang == 'en'>Alerts<#else>Alarmlar</#if></b><p><#if currentLang == 'en'>Create smart alerts for prices and indicators.<#else>Fiyat ve göstergelere göre akıllı alarmlar oluşturun.</#if></p></div></div>
                <div class="fp-feature-row"><span>▤</span><div><b><#if currentLang == 'en'>Notes<#else>Notlar</#if></b><p><#if currentLang == 'en'>Save strategies and investment ideas.<#else>Stratejilerinizi ve önemli görüşlerinizi kaydedin.</#if></p></div></div>
                <div class="fp-feature-row"><span>△</span><div><b><#if currentLang == 'en'>Simulation<#else>Simülasyon</#if></b><p><#if currentLang == 'en'>Test strategies before taking real risk.<#else>Stratejilerinizi risksiz şekilde test edin.</#if></p></div></div>
            </section>

            <section class="fp-showcase-panel fp-showcase-right">
                <div class="fp-panel-head fp-panel-head-accent">
                    <span class="fp-panel-icon">◎</span>
                    <div>
                        <strong><#if currentLang == 'en'>MARKET &amp; NEWS CENTER<#else>PİYASA &amp; HABER MERKEZİ</#if></strong>
                        <p><#if currentLang == 'en'>Follow live markets and never miss opportunities.<#else>Piyasaları anlık takip edin, fırsatları kaçırmayın.</#if></p>
                    </div>
                </div>
                <div class="fp-feature-row fp-teal"><span>▥</span><div><b><#if currentLang == 'en'>Market Data<#else>Piyasa Verileri</#if></b><p><#if currentLang == 'en'>Stocks, FX, crypto and indices in real time.<#else>Hisse, döviz, kripto ve endeksleri canlı takip edin.</#if></p></div></div>
                <div class="fp-feature-row fp-teal"><span>⌁</span><div><b><#if currentLang == 'en'>Technical Analysis<#else>Teknik Analiz</#if></b><p><#if currentLang == 'en'>Use advanced charts and indicators.<#else>Gelişmiş grafikler ve göstergelerle analiz yapın.</#if></p></div></div>
                <div class="fp-feature-row fp-teal"><span>▣</span><div><b><#if currentLang == 'en'>News Feed<#else>Haber Akışı</#if></b><p><#if currentLang == 'en'>Read finance news and market developments.<#else>Finans dünyasından anlık haberleri okuyun.</#if></p></div></div>
                <div class="fp-feature-row fp-teal"><span>☷</span><div><b><#if currentLang == 'en'>Market Analysis<#else>Piyasa Analizi</#if></b><p><#if currentLang == 'en'>Keep the pulse of the market, news and your portfolio with AI support.<#else>AI desteğiyle piyasanın, haberlerin ve portföyünüzün nabzını tutun.</#if></p></div></div>
            </section>

            <section class="fp-showcase-strip">
                <div><span>◆</span><b><#if currentLang == 'en'>SECURE LOGIN<#else>GÜVENLİ GİRİŞ</#if></b><p><#if currentLang == 'en'>Protected with modern security standards.<#else>Verileriniz yüksek güvenlik standartlarıyla korunur.</#if></p></div>
                <div><span>⚡</span><b><#if currentLang == 'en'>FAST &amp; LIVE<#else>HIZLI &amp; ANLIK</#if></b><p><#if currentLang == 'en'>Reach real-time data without delay.<#else>Gerçek zamanlı verilerle piyasaya anında ulaşın.</#if></p></div>
                <div><span>◔</span><b><#if currentLang == 'en'>SMART MANAGEMENT<#else>AKILLI YÖNETİM</#if></b><p><#if currentLang == 'en'>Analyze your portfolio and act clearly.<#else>Verilerinizi analiz edin, doğru kararlar alın.</#if></p></div>
                <div><span>★</span><b><#if currentLang == 'en'>CUSTOMIZABLE<#else>KİŞİSELLEŞTİRİLEBİLİR</#if></b><p><#if currentLang == 'en'>Shape the platform around your strategy.<#else>Kendi stratejinize göre uygulamayı kullanın.</#if></p></div>
            </section>
        </div>
        <script>
        (function () {
            var showcase = document.querySelector('.fp-login-showcase');
            if (showcase && showcase.parentNode !== document.body) {
                document.body.insertBefore(showcase, document.body.firstChild);
            }
            document.querySelectorAll('.fp-locale-nav, .fp-theme-toggle').forEach(function (control) {
                if (control.parentNode !== document.body) {
                    document.body.appendChild(control);
                }
            });
        })();
        </script>

        <#-- ─── Language switcher — fixed top-right ─────────────────
             Primary path  : realm has i18n enabled → use locale.supported
                             (provides proper URLs preserving all query params)
             Fallback path : locale not configured  → static TR/EN buttons;
                             JS rewrites href to append ?kc_locale=XX while
                             preserving all existing query parameters.
             Active locale detection falls back to <html lang="XX"> when
             locale.current is unavailable.
        ──────────────────────────────────────────────────────────── -->
        <#if locale?? && locale.supported?? && locale.supported?size gt 1>
        <nav class="fp-locale-nav" aria-label="<#if currentLang == 'en'>Language selector<#else>Dil seçici</#if>">
            <#list locale.supported as l>
            <a href="${l.url}"
               class="fp-locale-btn<#if locale.current == l.languageTag> fp-locale-active</#if>"
               hreflang="${l.languageTag}"
               lang="${l.languageTag}"
               title="${l.label}">
                ${l.languageTag?upper_case}
            </a>
            </#list>
        </nav>
        <#else>
        <#-- Fallback: always render TR/EN; JS fills in correct kc_locale URLs -->
        <nav class="fp-locale-nav" aria-label="<#if currentLang == 'en'>Language selector<#else>Dil seçici</#if>" id="fp-locale-nav-fallback">
            <a class="fp-locale-btn" lang="tr" href="?kc_locale=tr">TR</a>
            <a class="fp-locale-btn" lang="en" href="?kc_locale=en">EN</a>
        </nav>
        <script>
        (function () {
            var nav = document.getElementById('fp-locale-nav-fallback');
            if (!nav) return;
            var currentLang = (document.documentElement.lang || 'tr').split('-')[0].toLowerCase();
            <#if locale?? && locale.current??>
            currentLang = '${locale.current?split("-")[0]}';
            </#if>
            nav.querySelectorAll('.fp-locale-btn').forEach(function (btn) {
                var l = btn.getAttribute('lang');
                if (l === currentLang) btn.classList.add('fp-locale-active');
                try {
                    var url = new URL(window.location.href);
                    url.searchParams.set('kc_locale', l);
                    btn.href = url.toString();
                } catch (e) {
                    btn.href = '?kc_locale=' + l;
                }
            });
        })();
        </script>
        </#if>

        <button id="fp-theme-toggle"
                class="fp-theme-toggle"
                type="button"
                data-light-label="<#if currentLang == 'en'>Light<#else>Açık</#if>"
                data-dark-label="<#if currentLang == 'en'>Dark<#else>Koyu</#if>"
                aria-label="<#if currentLang == 'en'>Switch theme<#else>Temayı değiştir</#if>">
            <span class="fp-theme-toggle-dot" aria-hidden="true"></span>
            <span class="fp-theme-toggle-text"><#if currentLang == 'en'>Dark<#else>Koyu</#if></span>
        </button>
        <script>
        (function () {
            var root = document.documentElement;
            var toggle = document.getElementById('fp-theme-toggle');
            var storageKey = 'financePortalLoginTheme';

            function applyTheme(theme) {
                root.classList.toggle('fp-theme-dark', theme === 'dark');
                root.classList.toggle('fp-theme-light', theme === 'light');
                if (!toggle) return;

                var lightLabel = toggle.getAttribute('data-light-label') || 'Light';
                var darkLabel = toggle.getAttribute('data-dark-label') || 'Dark';
                var isSystemDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
                var isDark = theme === 'dark' || (!theme && isSystemDark);
                toggle.querySelector('.fp-theme-toggle-text').textContent = isDark ? darkLabel : lightLabel;
                toggle.setAttribute('aria-pressed', isDark ? 'true' : 'false');
            }

            var savedTheme = null;
            try {
                savedTheme = localStorage.getItem(storageKey);
            } catch (e) {}
            if (savedTheme !== 'dark' && savedTheme !== 'light') savedTheme = null;
            applyTheme(savedTheme);

            if (toggle) {
                toggle.addEventListener('click', function () {
                    var nextTheme = root.classList.contains('fp-theme-dark') ? 'light' : 'dark';
                    try {
                        localStorage.setItem(storageKey, nextTheme);
                    } catch (e) {}
                    applyTheme(nextTheme);
                });
            }

            document.querySelectorAll('.fp-locale-nav, .fp-theme-toggle').forEach(function (control) {
                if (control.parentNode !== document.body) {
                    document.body.appendChild(control);
                }
            });
        })();
        </script>
        <#-- ─── Brand block: logo + title ──────────────────────────── -->
                <script id="fp-login-translations">
        (function () {
            var translations = {
                tr: {
                    brandName: 'FİNANS PORTALI',
                    portfolioHead: 'PORTFÖY YÖNETİMİ',
                    portfolioHeadText: 'Varlıklarınızı tek ekrandan yönetin, performansınızı artırın.',
                    portfolioTracking: 'Portföy Takibi',
                    portfolioTrackingText: 'Tüm varlıklarınızı anlık olarak takip edin.',
                    alerts: 'Alarmlar',
                    alertsText: 'Fiyat ve göstergelere göre akıllı alarmlar oluşturun.',
                    notes: 'Notlar',
                    notesText: 'Stratejilerinizi ve önemli görüşlerinizi kaydedin.',
                    simulation: 'Simülasyon',
                    simulationText: 'Stratejilerinizi risksiz şekilde test edin.',
                    marketHead: 'PİYASA & HABER MERKEZİ',
                    marketHeadText: 'Piyasaları anlık takip edin, fırsatları kaçırmayın.',
                    marketData: 'Piyasa Verileri',
                    marketDataText: 'Hisse, döviz, kripto ve endeksleri canlı takip edin.',
                    technicalAnalysis: 'Teknik Analiz',
                    technicalAnalysisText: 'Gelişmiş grafikler ve göstergelerle analiz yapın.',
                    newsFeed: 'Haber Akışı',
                    newsFeedText: 'Finans dünyasından anlık haberleri okuyun.',
                    marketAnalysis: 'Piyasa Analizi',
                    marketAnalysisText: 'AI desteğiyle piyasanın, haberlerin ve portföyünüzün nabzını tutun.',
                    secureLogin: 'GÜVENLİ GİRİŞ',
                    secureLoginText: 'Verileriniz yüksek güvenlik standartlarıyla korunur.',
                    fastLive: 'HIZLI & ANLIK',
                    fastLiveText: 'Gerçek zamanlı verilerle piyasaya anında ulaşın.',
                    smartManagement: 'AKILLI YÖNETİM',
                    smartManagementText: 'Verilerinizi analiz edin, doğru kararlar alın.',
                    customizable: 'KİŞİSELLEŞTİRİLEBİLİR',
                    customizableText: 'Kendi stratejinize göre uygulamayı kullanın.',
                    heroTitle: 'Tüm finansal varlıklarınızı bir arada tutarak piyasa takibini ve analizi kolaylaştıran dijital alanınız.',
                    heroCopy: 'Piyasa verileri, haberler, teknik analiz ve daha fazlası tek platformda.',
                    usernameOrEmail: 'Kullanıcı Adı veya E-Posta',
                    password: 'Şifre',
                    rememberMe: 'Beni Hatırla',
                    forgotPassword: 'Şifremi Unuttum',
                    login: 'Giriş yap',
                    noAccount: 'Hesabınız yok mu?',
                    doRegister: 'Kayıt Ol',
                    showPassword: 'Şifreyi Göster',
                    hidePassword: 'Şifreyi Gizle',
                    themeLight: 'Açık',
                    themeDark: 'Koyu',
                    switchTheme: 'Temayı değiştir',
                    languageSelector: 'Dil seçici',
                    logoAlt: 'Finans Portalı logosu'
                },
                en: {
                    brandName: 'FINANCE PORTAL',
                    portfolioHead: 'PORTFOLIO MANAGEMENT',
                    portfolioHeadText: 'Track assets and improve performance from one screen.',
                    portfolioTracking: 'Portfolio Tracking',
                    portfolioTrackingText: 'Monitor all assets instantly.',
                    alerts: 'Alerts',
                    alertsText: 'Create smart alerts for prices and indicators.',
                    notes: 'Notes',
                    notesText: 'Save strategies and important insights.',
                    simulation: 'Simulation',
                    simulationText: 'Test strategies without real risk.',
                    marketHead: 'MARKET & NEWS CENTER',
                    marketHeadText: 'Follow live markets and never miss opportunities.',
                    marketData: 'Market Data',
                    marketDataText: 'Track stocks, FX, crypto and indices live.',
                    technicalAnalysis: 'Technical Analysis',
                    technicalAnalysisText: 'Analyze with advanced charts and indicators.',
                    newsFeed: 'News Feed',
                    newsFeedText: 'Read instant news from the finance world.',
                    marketAnalysis: 'Market Analysis',
                    marketAnalysisText: 'Keep the pulse of the market, news and your portfolio with AI support.',
                    secureLogin: 'SECURE LOGIN',
                    secureLoginText: 'Your data is protected with high security standards.',
                    fastLive: 'FAST & LIVE',
                    fastLiveText: 'Reach real-time market data instantly.',
                    smartManagement: 'SMART MANAGEMENT',
                    smartManagementText: 'Analyze your data and make clear decisions.',
                    customizable: 'CUSTOMIZABLE',
                    customizableText: 'Use the platform according to your own strategy.',
                    heroTitle: 'Your digital space for keeping all financial assets together while making market tracking and analysis easier.',
                    heroCopy: 'Market data, news, technical analysis and more on a single platform.',
                    usernameOrEmail: 'Username or Email',
                    password: 'Password',
                    rememberMe: 'Remember Me',
                    forgotPassword: 'Forgot Password',
                    login: 'Sign In',
                    noAccount: "Don't have an account?",
                    doRegister: 'Sign Up',
                    showPassword: 'Show Password',
                    hidePassword: 'Hide Password',
                    themeLight: 'Light',
                    themeDark: 'Dark',
                    switchTheme: 'Switch theme',
                    languageSelector: 'Language selector',
                    logoAlt: 'Finance Portal logo'
                }
            };

            function setText(selector, value) {
                var el = document.querySelector(selector);
                if (el) el.textContent = value;
            }

            function getSelectedLang() {
                try {
                    var urlLang = new URL(window.location.href).searchParams.get('kc_locale');
                    if (urlLang === 'tr' || urlLang === 'en') {
                        localStorage.setItem('financePortalLoginLang', urlLang);
                        return urlLang;
                    }
                } catch (e) {}

                var active = document.querySelector('.fp-locale-active');
                var activeText = active ? (active.getAttribute('lang') || active.getAttribute('hreflang') || active.textContent || '') : '';
                if (/^en/i.test(activeText.trim())) return 'en';
                if (/^tr/i.test(activeText.trim())) return 'tr';

                try {
                    var saved = localStorage.getItem('financePortalLoginLang');
                    if (saved === 'tr' || saved === 'en') return saved;
                } catch (e) {}

                return ((document.documentElement.lang || 'tr').split('-')[0] === 'en') ? 'en' : 'tr';
            }

            function updateRows(rows, keys, t) {
                rows.forEach(function (row, index) {
                    var pair = keys[index];
                    if (!pair) return;
                    var title = row.querySelector('b');
                    var text = row.querySelector('p');
                    if (title) title.textContent = t[pair[0]];
                    if (text) text.textContent = t[pair[1]];
                });
            }

            function syncActiveLocale(lang) {
                document.querySelectorAll('.fp-locale-btn').forEach(function (btn) {
                    var code = (btn.getAttribute('lang') || btn.getAttribute('hreflang') || btn.textContent || '').trim().slice(0, 2).toLowerCase();
                    btn.classList.toggle('fp-locale-active', code === lang);
                });
            }

            function updateThemeToggle(t) {
                var toggle = document.getElementById('fp-theme-toggle');
                if (!toggle) return;
                toggle.setAttribute('aria-label', t.switchTheme);
                toggle.setAttribute('data-light-label', t.themeLight);
                toggle.setAttribute('data-dark-label', t.themeDark);
                var label = toggle.querySelector('.fp-theme-toggle-text');
                var isDark = document.documentElement.classList.contains('fp-theme-dark') || (!document.documentElement.classList.contains('fp-theme-light') && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
                if (label) label.textContent = isDark ? t.themeDark : t.themeLight;
            }

            function applyLoginTranslations() {
                var lang = getSelectedLang();
                var t = translations[lang] || translations.tr;
                document.documentElement.lang = lang;
                syncActiveLocale(lang);

                setText('.fp-brand-name', t.brandName);
                setText('.fp-hero-title', t.heroTitle);
                setText('.fp-hero-copy', t.heroCopy);
                setText('.fp-showcase-left .fp-panel-head strong', t.portfolioHead);
                setText('.fp-showcase-left .fp-panel-head p', t.portfolioHeadText);
                updateRows(document.querySelectorAll('.fp-showcase-left .fp-feature-row'), [
                    ['portfolioTracking', 'portfolioTrackingText'],
                    ['alerts', 'alertsText'],
                    ['notes', 'notesText'],
                    ['simulation', 'simulationText']
                ], t);

                setText('.fp-showcase-right .fp-panel-head strong', t.marketHead);
                setText('.fp-showcase-right .fp-panel-head p', t.marketHeadText);
                updateRows(document.querySelectorAll('.fp-showcase-right .fp-feature-row'), [
                    ['marketData', 'marketDataText'],
                    ['technicalAnalysis', 'technicalAnalysisText'],
                    ['newsFeed', 'newsFeedText'],
                    ['marketAnalysis', 'marketAnalysisText']
                ], t);

                updateRows(document.querySelectorAll('.fp-showcase-strip > div'), [
                    ['secureLogin', 'secureLoginText'],
                    ['fastLive', 'fastLiveText'],
                    ['smartManagement', 'smartManagementText'],
                    ['customizable', 'customizableText']
                ], t);

                var username = document.getElementById('username');
                var password = document.getElementById('password');
                var submit = document.getElementById('kc-login');
                var logo = document.querySelector('.fp-brand-logo');
                var passwordToggle = document.getElementById('fp-password-toggle');

                setText('label[for="username"]', t.usernameOrEmail);
                setText('label[for="password"]', t.password);
                setText('.fp-check-text', t.rememberMe);
                setText('.fp-forgot-link', t.forgotPassword);
                setText('.fp-signup-row-inline .fp-signup-text', t.noAccount);
                setText('.fp-signup-row-inline .fp-signup-link', t.doRegister);
                if (username) username.placeholder = t.usernameOrEmail;
                if (password) password.placeholder = t.password;
                if (submit) submit.value = t.login;
                if (logo) logo.alt = t.logoAlt;
                if (passwordToggle) {
                    passwordToggle.setAttribute('data-show-label', t.showPassword);
                    passwordToggle.setAttribute('data-hide-label', t.hidePassword);
                    passwordToggle.setAttribute('aria-label', password && password.type === 'text' ? t.hidePassword : t.showPassword);
                }
                document.querySelectorAll('.fp-locale-nav').forEach(function (nav) { nav.setAttribute('aria-label', t.languageSelector); });
                updateThemeToggle(t);
            }

            window.fpApplyLoginTranslations = applyLoginTranslations;

            document.addEventListener('DOMContentLoaded', function () {
                document.querySelectorAll('.fp-locale-btn').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        var lang = (btn.getAttribute('lang') || btn.getAttribute('hreflang') || btn.textContent || '').trim().slice(0, 2).toLowerCase();
                        if (lang === 'tr' || lang === 'en') {
                            try { localStorage.setItem('financePortalLoginLang', lang); } catch (e) {}
                        }
                    });
                });
                applyLoginTranslations();
            });
        })();
        </script>
<div class="fp-brand">
            <img class="fp-brand-logo"
                 src="${url.resourcesPath}/img/finans-portali-logo.png"
                 width="64"
                 height="64"
                 alt="<#if currentLang == 'en'>Finance Portal logo<#else>Finans Portalı logosu</#if>" />
            <div class="fp-brand-name">${msg("brandName")}</div>
            <h1 class="fp-hero-title"><#if currentLang == 'en'>Your digital space for keeping all financial assets together while making market tracking and analysis easier.<#else>Tüm finansal varlıklarınızı bir arada tutarak piyasa takibini ve analizi kolaylaştıran dijital alanınız.</#if></h1>
            <p class="fp-hero-copy"><#if currentLang == 'en'>Market data, news, technical analysis and more on a single platform.<#else>Piyasa verileri, haberler, teknik analiz ve daha fazlası tek platformda.</#if></p>
        </div>

    <#-- ═══════════════════════════════════════════════════════════
         FORM SECTION — the login form
         All Keycloak-required IDs, names and form action preserved.
         ═══════════════════════════════════════════════════════════ -->
    <#elseif section = "form">
        <#if realm.password>
        <form id="kc-form-login"
              onsubmit="login.disabled = true; return true;"
              action="${url.loginAction}"
              method="post">

            <#-- Username / Email field -->
            <div class="${properties.kcFormGroupClass!} fp-field">
                <label for="username" class="${properties.kcLabelClass!}">
                    <#if !realm.loginWithEmailAllowed>
                        ${msg("username")}
                    <#elseif !realm.registrationEmailAsUsername>
                        ${msg("usernameOrEmail")}
                    <#else>
                        ${msg("email")}
                    </#if>
                </label>
                <div class="fp-input-wrap">
                    <span class="fp-icon" aria-hidden="true">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                             viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                            <circle cx="12" cy="7" r="4"/>
                        </svg>
                    </span>
                    <input tabindex="1"
                           id="username"
                           class="${properties.kcInputClass!}"
                           name="username"
                           value="${(login.username!'')}"
                           type="text"
                           autofocus
                           autocomplete="username"
                           aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                           placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>" />
                </div>
                <#if messagesPerField.existsError('username','password')>
                <span id="input-error" class="fp-field-error" aria-live="polite">
                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                </span>
                </#if>
            </div>

            <#-- Password field with visibility toggle -->
            <div class="${properties.kcFormGroupClass!} fp-field">
                <label for="password" class="${properties.kcLabelClass!}">
                    ${msg("password")}
                </label>
                <div class="fp-input-wrap ${properties.kcInputGroup!}">
                    <span class="fp-icon" aria-hidden="true">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16"
                             viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                        </svg>
                    </span>
                    <input tabindex="2"
                           id="password"
                           class="${properties.kcInputClass!}"
                           name="password"
                           type="password"
                           autocomplete="current-password"
                           aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                           placeholder="${msg("password")}" />
                    <button id="fp-password-toggle"
                            class="${properties.kcFormPasswordVisibilityButtonClass!}"
                            type="button"
                            aria-label="${msg("showPassword")}"
                            aria-pressed="false"
                            aria-controls="password"
                            data-cy="showPasswordButton">
                        <i id="fp-password-toggle-icon"
                           class="${properties.kcFormPasswordVisibilityIconShow!}"
                           aria-hidden="true"></i>
                    </button>
                </div>
            </div>

            <#-- Remember me + Forgot password row -->
            <div class="fp-options-row" id="kc-form-options">
                <div>
                    <#if realm.rememberMe && !usernameEditDisabled??>
                    <label class="fp-check-label">
                        <input tabindex="3"
                               id="rememberMe"
                               name="rememberMe"
                               type="checkbox"
                               <#if login.rememberMe??>checked</#if> />
                        <span class="fp-check-box" aria-hidden="true"></span>
                        <span class="fp-check-text">${msg("rememberMe")}</span>
                    </label>
                    </#if>
                </div>
                <div>
                    <#if realm.resetPasswordAllowed>
                    <a tabindex="5"
                       href="${url.loginResetCredentialsUrl}"
                       class="fp-forgot-link">
                        ${msg("doForgotPassword")}
                    </a>
                    </#if>
                </div>
            </div>

            <#-- Submit -->
            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input type="hidden"
                       id="id-hidden-input"
                       name="credentialId"
                       <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if> />
                <input tabindex="4"
                       class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       name="login"
                       id="kc-login"
                       type="submit"
                       value="${msg("doLogIn")}" />
            </div>

            <#-- Inline registration link -->
            <#if realm.registrationAllowed && !registrationDisabled??>
            <div class="fp-signup-row fp-signup-row-inline">
                <span class="fp-signup-text">${msg("noAccount")}</span>
                <a tabindex="6"
                   href="${url.registrationUrl}"
                   class="fp-signup-link">
                    ${msg("doRegister")}
                </a>
            </div>
            </#if>
        </form>
        <script>
        (function () {
            var password = document.getElementById('password');
            var toggle = document.getElementById('fp-password-toggle');
            var icon = document.getElementById('fp-password-toggle-icon');
            if (!password || !toggle) return;

            var showLabel = toggle.getAttribute('data-show-label') || '${msg("showPassword")?js_string}';
            var hideLabel = toggle.getAttribute('data-hide-label') || '${msg("hidePassword")?js_string}';
            var showIcon = '${(properties.kcFormPasswordVisibilityIconShow!'')?js_string}';
            var hideIcon = '${(properties.kcFormPasswordVisibilityIconHide!'')?js_string}';

            toggle.addEventListener('click', function () {
                var isHidden = password.type === 'password';
                password.type = isHidden ? 'text' : 'password';
                showLabel = toggle.getAttribute('data-show-label') || showLabel;
                hideLabel = toggle.getAttribute('data-hide-label') || hideLabel;
                toggle.setAttribute('aria-label', isHidden ? hideLabel : showLabel);
                toggle.setAttribute('aria-pressed', isHidden ? 'true' : 'false');

                if (icon && showIcon && hideIcon) {
                    icon.className = isHidden ? hideIcon : showIcon;
                }
            });
        })();
        </script>
        </#if>

    <#-- ═══════════════════════════════════════════════════════════
         INFO SECTION — rendered below the card
         Registration link shown when signup is allowed.
         ═══════════════════════════════════════════════════════════ -->
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
        <div id="kc-registration-container" class="fp-signup-row">
            <span class="fp-signup-text">${msg("noAccount")}</span>
            <a tabindex="6"
               href="${url.registrationUrl}"
               class="fp-signup-link">
                ${msg("doRegister")}
            </a>
        </div>
        </#if>
    </#if>

</@layout.registrationLayout>
