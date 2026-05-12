import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { permBlockUser, tempBlockUser, unblockUser } from "../../api/userApi";
import { extractErrorMessage } from "../../api/responseUtils";
import ErrorMessage from "../common/ErrorMessage";

const DEFAULT_FORM = {
  reason: "",
  blockedUntil: "",
};

function toDateTimeLocalValue(value) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function getCurrentMinDateTime() {
  return toDateTimeLocalValue(new Date());
}

export default function ModerationActionModal({ mode, isOpen, user, onClose, onSuccess }) {
  const { t } = useTranslation();
  const [form, setForm] = useState(DEFAULT_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const copy = useMemo(() => {
    switch (mode) {
      case "temp":
        return {
          eyebrow: t("admin.users.moderation.tempBlockEyebrow"),
          submit: t("admin.users.moderation.tempBlockSubmit"),
          error: t("admin.users.moderation.tempBlockError"),
        };
      case "perm":
        return {
          eyebrow: t("admin.users.moderation.permBlockEyebrow"),
          submit: t("admin.users.moderation.permBlockSubmit"),
          error: t("admin.users.moderation.permBlockError"),
        };
      case "unblock":
        return {
          eyebrow: t("admin.users.moderation.unblockEyebrow"),
          submit: t("admin.users.moderation.unblockSubmit"),
          error: t("admin.users.moderation.unblockError"),
        };
      default:
        return null;
    }
  }, [mode, t]);

  useEffect(() => {
    if (!isOpen) {
      setForm(DEFAULT_FORM);
      setSubmitting(false);
      setError("");
    }
  }, [isOpen]);

  if (!isOpen || !user || !copy) {
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      let moderation;

      if (mode === "temp") {
        if (!form.blockedUntil) {
          setError(t("admin.users.moderation.blockedUntilRequired"));
          setSubmitting(false);
          return;
        }

        const selected = new Date(form.blockedUntil);
        if (Number.isNaN(selected.getTime()) || selected <= new Date()) {
          setError(t("admin.users.moderation.blockedUntilFuture"));
          setSubmitting(false);
          return;
        }

        moderation = await tempBlockUser(user.id, {
          reason: form.reason.trim() || null,
          blockedUntil: selected.toISOString().slice(0, 19),
        });
      } else if (mode === "perm") {
        moderation = await permBlockUser(user.id, {
          reason: form.reason.trim() || null,
        });
      } else {
        moderation = await unblockUser(user.id, {
          reason: form.reason.trim() || null,
        });
      }

      onSuccess?.(moderation);
    } catch (submitError) {
      setError(extractErrorMessage(submitError, copy.error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="auth-modal instrument-action-modal"
        role="dialog"
        aria-modal="true"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="instrument-action-modal-head">
          <div>
            <p className="eyebrow">{copy.eyebrow}</p>
            <h3>{user.fullName || user.email}</h3>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            {t("common.close")}
          </button>
        </div>

        {error ? <ErrorMessage message={error} /> : null}

        <form className="instrument-action-form" onSubmit={handleSubmit}>
          <label className="portfolio-field">
            <span>{t("admin.users.moderation.reasonLabel")}</span>
            <textarea
              rows="4"
              value={form.reason}
              onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))}
              className="admin-users-textarea"
              placeholder={t("admin.users.moderation.reasonPlaceholder")}
            />
          </label>

          {mode === "temp" ? (
            <label className="portfolio-field">
              <span>{t("admin.users.moderation.blockedUntilLabel")}</span>
              <input
                type="datetime-local"
                required
                min={getCurrentMinDateTime()}
                value={form.blockedUntil}
                onChange={(event) => setForm((current) => ({ ...current, blockedUntil: event.target.value }))}
              />
            </label>
          ) : null}

          <div className="instrument-action-footer">
            <button type="submit" disabled={submitting}>
              {submitting ? t("admin.users.moderation.submitting") : copy.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
