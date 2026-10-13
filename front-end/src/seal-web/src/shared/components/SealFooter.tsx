import { useNavigate } from "react-router";
import { Github } from "lucide-react";
import { C } from "@/shared/components/PixelComponents";
import { useRules } from "@/app/providers/RulesProvider";
import sealLogo from "@/imports/image.png";

const GITHUB_URL = "https://github.com/nhatnhm1405/seal-hackathon-system.git";
const CONTACT_EMAIL = "sealhackathon.system@gmail.com";
const mono = "'JetBrains Mono', monospace";

type FooterLink = {
  label: string;
  to?: string;
  href?: string;
  external?: boolean;
  icon?: typeof Github;
  action?: () => void;
};

export function SealFooter() {
  const navigate = useNavigate();
  const { openRules, rulesLinkLabel } = useRules();

  const teamLinks: FooterLink[] = [
    { label: "About the Project", to: "/about" },
    { label: "Our Team", to: "/team" },
    { label: rulesLinkLabel, action: openRules },
    { label: "GitHub", href: GITHUB_URL, external: true, icon: Github },
  ];

  function handleClick(link: FooterLink, e: React.MouseEvent) {
    if (link.external) return;
    e.preventDefault();
    if (link.action) { link.action(); return; }
    if (!link.to) return;
    if (link.to.startsWith("/#")) {
      const hash = link.to.slice(1);
      navigate("/");
      setTimeout(() => {
        const el = document.querySelector(hash);
        el?.scrollIntoView({ behavior: "smooth" });
      }, 50);
    } else {
      navigate(link.to);
      window.scrollTo({ top: 0 });
    }
  }

  const linkBase: React.CSSProperties = {
    color: C.textMuted,
    fontFamily: mono,
    fontSize: 13,
    textDecoration: "none",
    transition: "color 0.15s, text-shadow 0.15s, border-color 0.15s",
    display: "inline-flex",
    alignItems: "center",
    gap: 8,
    width: "fit-content",
    borderBottom: "1px solid transparent",
    paddingBottom: 2,
  };

  function hoverOn(e: React.MouseEvent) {
    const el = e.currentTarget as HTMLElement;
    el.style.color = C.green;
    el.style.textShadow = `0 0 10px ${C.green}`;
    el.style.borderBottomColor = C.green;
  }
  function hoverOff(e: React.MouseEvent) {
    const el = e.currentTarget as HTMLElement;
    el.style.color = C.textMuted;
    el.style.textShadow = "none";
    el.style.borderBottomColor = "transparent";
  }

  return (
    <footer
      style={{
        background: C.footerBg,
        borderTop: `1px solid ${C.border}`,
        padding: "64px 24px 28px",
        width: "100%",
        position: "relative",
      }}
    >
      {/* Top neon accent line */}
      <div
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          height: 1,
          background:
            "linear-gradient(90deg, transparent, rgba(34,197,94,0.5), rgba(59,130,246,0.4), transparent)",
        }}
      />

      <div style={{ maxWidth: 1100, margin: "0 auto" }}>
        <div
          className="grid grid-cols-1 md:grid-cols-2 gap-12 md:gap-16 mb-12"
          style={{ alignItems: "start" }}
        >
          {/* BRAND BLOCK */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 20,
              minWidth: 0,
            }}
          >
            <img
              src={sealLogo}
              alt="SEAL"
              style={{
                height: 180,
                width: "auto",
                objectFit: "contain",
                flexShrink: 0,
                filter:
                  "drop-shadow(0 0 14px rgba(34,197,94,0.55)) drop-shadow(0 0 28px rgba(59,130,246,0.3))",
              }}
            />
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                gap: 10,
                minWidth: 0,
                flex: 1,
              }}
            >
              <span
                style={{
                  fontFamily: mono,
                  fontWeight: 900,
                  fontSize: "clamp(24px, 2.6vw, 34px)",
                  letterSpacing: "0.14em",
                  lineHeight: 1,
                  background: C.gradientPrimary,
                  WebkitBackgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                  backgroundClip: "text",
                  textShadow: "0 0 28px rgba(34,197,94,0.3)",
                  whiteSpace: "nowrap",
                  width: "max-content",
                }}
              >
                SEAL HACKATHON
              </span>
              <span
                style={{
                  color: C.textMuted,
                  fontFamily: mono,
                  fontSize: "clamp(10px, 0.95vw, 12px)",
                  letterSpacing: "0.14em",
                  lineHeight: 1.2,
                  whiteSpace: "nowrap",
                }}
              >
                Software Engineering Agile League HACKATHON
              </span>
            </div>
          </div>

          {/* THE TEAM + CONTACT */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 28,
            }}
            className="md:items-end"
          >
            <div className="w-full md:max-w-xs">
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  marginBottom: 16,
                }}
              >
                <span
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: "50%",
                    background: C.green,
                    boxShadow: `0 0 8px ${C.green}`,
                  }}
                />
                <span
                  style={{
                    color: C.green,
                    fontFamily: mono,
                    fontSize: 10,
                    letterSpacing: "0.22em",
                    textTransform: "uppercase",
                  }}
                >
                  The Team
                </span>
                <div
                  style={{
                    flex: 1,
                    height: 1,
                    background:
                      "linear-gradient(90deg, rgba(34,197,94,0.35), transparent)",
                  }}
                />
              </div>
              <div className="flex flex-col gap-3">
                {teamLinks.map((l) => {
                  const Icon = l.icon;
                  return (
                    <a
                      key={l.label}
                      href={l.external ? l.href : l.to ?? "#"}
                      target={l.external ? "_blank" : undefined}
                      rel={l.external ? "noopener noreferrer" : undefined}
                      onClick={(e) => handleClick(l, e)}
                      style={linkBase}
                      onMouseEnter={hoverOn}
                      onMouseLeave={hoverOff}
                    >
                      {Icon && <Icon size={14} />}
                      {l.label}
                    </a>
                  );
                })}
              </div>
            </div>

            <div className="w-full md:max-w-xs">
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  marginBottom: 12,
                }}
              >
                <span
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: "50%",
                    background: C.green,
                    boxShadow: `0 0 8px ${C.green}`,
                  }}
                />
                <span
                  style={{
                    color: C.green,
                    fontFamily: mono,
                    fontSize: 10,
                    letterSpacing: "0.22em",
                    textTransform: "uppercase",
                  }}
                >
                  Contact
                </span>
                <div
                  style={{
                    flex: 1,
                    height: 1,
                    background:
                      "linear-gradient(90deg, rgba(34,197,94,0.35), transparent)",
                  }}
                />
              </div>
              <a
                href={`mailto:${CONTACT_EMAIL}`}
                style={linkBase}
                onMouseEnter={hoverOn}
                onMouseLeave={hoverOff}
              >
                {CONTACT_EMAIL}
              </a>
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div
          style={{
            borderTop: `1px solid ${C.border}`,
            paddingTop: 20,
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 12,
          }}
        >
          <span
            style={{
              color: C.copyright,
              fontFamily: mono,
              fontSize: 12,
              letterSpacing: "0.04em",
            }}
          >
            © 2026 SEAL Hackathon. All rights reserved.
          </span>
          <div
            style={{
              height: 1,
              flex: 1,
              maxWidth: 200,
              background:
                "linear-gradient(90deg, transparent, rgba(59,130,246,0.3), transparent)",
            }}
          />
          <span
            style={{
              color: C.blue,
              fontFamily: mono,
              fontSize: 10,
              letterSpacing: "0.14em",
            }}
          >
           
          </span>
        </div>
      </div>
    </footer>
  );
}
