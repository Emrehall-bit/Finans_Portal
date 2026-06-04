import { useRef, useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { MessageSquare, Minimize2, Send, Sparkles, Loader2, Lock } from "lucide-react";
import { postAiChat } from "../../api/aiApi";
import { useAuth } from "../../auth/AuthContext";
import AiResponseMeta from "./AiResponseMeta";

export default function AiAssistantWidget({ aiContext = null }) {
  const { pathname } = useLocation();
  const { isAuthenticated, login } = useAuth();
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([
    { role: "ai", text: t("aiCards.assistant.initialMessage") },
  ]);
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef(null);

  // Update the initial greeting when the UI language changes,
  // but only if the conversation hasn't started yet.
  useEffect(() => {
    setMessages((prev) => {
      if (prev.length === 1 && prev[0].role === "ai") {
        return [{ role: "ai", text: t("aiCards.assistant.initialMessage") }];
      }
      return prev;
    });
  }, [i18n.language, t]);

  useEffect(() => {
    if (open) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, open]);

  async function sendMessage(text) {
    const nextText = text.trim();
    if (!isAuthenticated) {
      login();
      return;
    }
    if (!nextText || loading) return;

    setMessages((prev) => [...prev, { role: "user", text: nextText }]);
    setInput("");
    setLoading(true);

    try {
      const data = await postAiChat(nextText, aiContext, i18n.language);
      setMessages((prev) => [...prev, { role: "ai", text: data.reply, metadata: data.metadata }]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          role: "ai",
          text: t("aiCards.assistant.errorReply"),
          error: true,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  if (pathname === "/economy") return null;

  if (!open) {
    return (
      <button
        type="button"
        className="ai-assistant-fab"
        onClick={() => setOpen(true)}
        aria-label={t("aiCards.assistant.openLabel")}
      >
        <Sparkles size={23} aria-hidden="true" />
      </button>
    );
  }

  return (
    <aside className="ai-assistant-panel" aria-label={t("aiCards.assistant.panelLabel")}>
      <header className="ai-assistant-head">
        <div className="ai-assistant-title">
          <span aria-hidden="true">
            <MessageSquare size={17} />
          </span>
          <div>
            <strong>{t("aiCards.assistant.title")}</strong>
            <small>{t("aiCards.assistant.subtitle")}</small>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setOpen(false)}
          aria-label={t("aiCards.assistant.minimizeLabel")}
        >
          <Minimize2 size={16} aria-hidden="true" />
        </button>
      </header>

      <div className="ai-assistant-messages">
        {!isAuthenticated ? (
          <div className="ai-assistant-locked-state">
            <span className="ai-assistant-locked-icon" aria-hidden="true">
              <Lock size={16} />
            </span>
            <strong>{t("aiCards.assistant.loginRequired")}</strong>
            <button type="button" className="ai-premium-gate-button" onClick={() => login()}>
              {t("aiCards.assistant.loginCta")}
            </button>
          </div>
        ) : null}

        {isAuthenticated ? messages.map((message, index) => (
          <div
            key={`${message.role}-${index}`}
            className={`ai-assistant-message ${message.role}${message.error ? " error" : ""}`}
          >
            <div>{message.text}</div>
            {message.role === "ai" && message.metadata ? (
              <AiResponseMeta metadata={message.metadata} />
            ) : null}
          </div>
        )) : null}

        {isAuthenticated && loading ? (
          <div className="ai-assistant-message ai typing">
            <Loader2 size={14} className="ai-assistant-spinner" aria-label={t("aiCards.assistant.waitingLabel")} />
          </div>
        ) : null}

        {isAuthenticated && messages.length <= 1 && !loading ? (
          <div className="ai-assistant-suggestions">
            <span>{t("aiCards.assistant.suggestions")}</span>
            {t("aiCards.assistant.suggestionList", { returnObjects: true }).map((suggestion) => (
              <button
                key={suggestion}
                type="button"
                onClick={() => sendMessage(suggestion)}
              >
                {suggestion}
              </button>
            ))}
          </div>
        ) : null}

        <div ref={bottomRef} />
      </div>

      <form
        className="ai-assistant-form"
        onSubmit={(event) => {
          event.preventDefault();
          sendMessage(input);
        }}
      >
        <div>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={isAuthenticated ? t("aiCards.assistant.inputPlaceholder") : t("aiCards.assistant.loginPlaceholder")}
            disabled={loading || !isAuthenticated}
            aria-label={t("aiCards.assistant.inputLabel")}
          />
          <button type="submit" aria-label={t("aiCards.assistant.sendLabel")} disabled={loading || !input.trim() || !isAuthenticated}>
            {loading ? (
              <Loader2 size={16} className="ai-assistant-spinner" aria-hidden="true" />
            ) : (
              <Send size={16} aria-hidden="true" />
            )}
          </button>
        </div>
        <small>{t("aiCards.assistant.disclaimer")}</small>
      </form>
    </aside>
  );
}
