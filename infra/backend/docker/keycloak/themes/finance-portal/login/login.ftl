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
        <nav class="fp-locale-nav" aria-label="Language selector">
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
        <nav class="fp-locale-nav" aria-label="Language selector" id="fp-locale-nav-fallback">
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

        <#-- ─── Brand block: logo + title ──────────────────────────── -->
        <div class="fp-brand">
            <img class="fp-brand-logo"
                 src="${url.resourcesPath}/img/finans-portali-logo.png"
                 width="64"
                 height="64"
                 alt="Finance Portal logo" />
            <div class="fp-brand-name">FINANCE PORTAL</div>
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
                    <button class="${properties.kcFormPasswordVisibilityButtonClass!}"
                            type="button"
                            aria-label="${msg("showPassword")}"
                            aria-controls="password"
                            data-cy="showPasswordButton">
                        <i class="${properties.kcFormPasswordVisibilityIconShow!}"
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
        </form>
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
