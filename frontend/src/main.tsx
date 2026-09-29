import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  CalendarDays,
  Users,
  Check,
  ChevronRight,
  ChevronLeft,
  LogOut,
  Mail,
  Download,
  Plus,
  ShieldCheck,
  Clock,
  ArrowUpLeft,
  LockKeyhole,
  Leaf,
} from "lucide-react";
import { api, login, refreshCsrf, Person, Team, Member, Week } from "./api";
import "./style.css";
const labels: Record<string, string> = {
  PENDING: "در انتظار بررسی",
  APPROVED: "تأیید شده",
  REJECTED: "رد شده",
  NOT_ATTENDING: "عدم حضور",
  UNDECLARED: "اعلام نشده",
};
const roleLabels: Record<string, string> = {
  ADMIN: "مدیر سیستم",
  MANAGER: "مدیر تیم",
  EMPLOYEE: "عضو تیم",
};
const persian = (
  d: string,
  opts: Intl.DateTimeFormatOptions = {
    day: "numeric",
    month: "long",
    year: "numeric",
  },
) => new Intl.DateTimeFormat("fa-IR", opts).format(new Date(d + "T12:00:00"));
function shift(d: string, n: number) {
  const x = new Date(d + "T12:00:00Z");
  x.setUTCDate(x.getUTCDate() + n);
  return x.toISOString().slice(0, 10);
}
function currentThursday() {
  const t = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Tehran",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  const day = new Date(t + "T12:00:00Z").getUTCDay();
  return shift(t, (4 - day + 7) % 7);
}
function Badge({ status }: { status: string }) {
  return (
    <span className={"badge " + status.toLowerCase()}>
      <i />
      {labels[status] || status}
    </span>
  );
}
function App() {
  const [user, setUser] = useState<Person | null>(null),
    [boot, setBoot] = useState(true),
    [teams, setTeams] = useState<Team[]>([]),
    [team, setTeam] = useState(""),
    [date, setDate] = useState(currentThursday),
    [week, setWeek] = useState<Week | null>(null),
    [tab, setTab] = useState("dashboard"),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(false),
    [finalConfirm, setFinalConfirm] = useState(false);
  async function loadProfile() {
    const p = await api<Person>("/me");
    setUser(p);
    const ts = await api<Team[]>("/teams");
    setTeams(ts);
    setTeam(p.teamId);
  }
  useEffect(() => {
    loadProfile()
      .catch(() => {})
      .finally(() => setBoot(false));
  }, []);
  const base = `/teams/${team}/weeks/${date}`;
  async function reload() {
    setWeek(await api<Week>(base));
  }
  useEffect(() => {
    if (!user || !team) return;
    let active = true;
    setLoading(true);
    setWeek(null);
    setError("");
    setFinalConfirm(false);
    api<Week>(base)
      .then((w) => {
        if (active) setWeek(w);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, team, date]);
  useEffect(() => {
    if (!week?.mail || week.mail.status !== "QUEUED") return;
    const timer = setInterval(() => {
      api<Week>(base)
        .then(setWeek)
        .catch(() => {});
    }, 15000);
    return () => clearInterval(timer);
  }, [week?.mail?.status, base]);
  async function act(fn: () => Promise<unknown>, message: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
      await reload();
      setNotice(message);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (boot) return <div className="splash">در حال آماده‌سازی نگاه…</div>;
  if (!user) return <Login onLogin={loadProfile} />;
  const manager = user.role !== "EMPLOYEE",
    selectedTeam = teams.find((t) => t.id === team),
    members = week?.members || [],
    own = members.find((m) => m.userId === user.id),
    locked = week?.status === "FINALIZED";
  const count = (s: string) =>
    members.filter((m) => m.thursdayStatus === s || m.fridayStatus === s)
      .length;
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">
            <Leaf size={25} />
          </span>
          <div>
            نگاه<span>همراه آخر هفته</span>
          </div>
        </div>
        <div className="workspace">
          <span className="tiny">فضای کاری</span>
          <strong>{selectedTeam?.name || "تیم من"}</strong>
          <span className="online">سامانه هماهنگی حضور</span>
        </div>
        <nav>
          <button
            className={tab === "dashboard" ? "active" : ""}
            onClick={() => setTab("dashboard")}
          >
            <CalendarDays size={19} />
            برنامه آخر هفته
          </button>
          {user.role === "ADMIN" && (
            <button
              className={tab === "admin" ? "active" : ""}
              onClick={() => setTab("admin")}
            >
              <Users size={19} />
              تیم‌ها و اعضا
            </button>
          )}
          <button
            className={tab === "account" ? "active" : ""}
            onClick={() => setTab("account")}
          >
            <LockKeyhole size={19} />
            حساب کاربری
          </button>
        </nav>
        <div className="sidebar-note">
          <ShieldCheck size={24} />
          <strong>هماهنگی ساده‌تر، کنار هم</strong>
          <p>درخواست‌ها، تأییدها و فهرست نهایی؛ همه در یک جا.</p>
        </div>
        <div className="profile">
          <div className="avatar">{user.fullName.slice(0, 1)}</div>
          <div>
            <strong>{user.fullName}</strong>
            <small>{roleLabels[user.role]}</small>
          </div>
          <button
            title="خروج"
            aria-label="خروج"
            onClick={async () => {
              try {
                await api("/logout", "POST");
                await refreshCsrf();
                setUser(null);
                setWeek(null);
                setTab("dashboard");
                setNotice("");
              } catch (e) {
                setError((e as Error).message);
              }
            }}
          >
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <main>
        <header className="topbar">
          <span>
            فضای کاری <span className="slash">/</span>{" "}
            {tab === "dashboard"
              ? "برنامه آخر هفته"
              : tab === "admin"
                ? "مدیریت تیم‌ها"
                : "حساب کاربری"}
          </span>
          <span className="today">
            <CalendarDays size={16} />
            {new Intl.DateTimeFormat("fa-IR", {
              dateStyle: "long",
              timeZone: "Asia/Tehran",
            }).format(new Date())}
          </span>
        </header>
        <div className="content">
          <div className="page-title">
            <div>
              <div className="eyebrow">NEGAH VOLUNTEER</div>
              <h1>
                {tab === "dashboard"
                  ? "آخر هفته را هماهنگ کنیم."
                  : tab === "admin"
                    ? "یک فضای مشترک برای تیم‌ها."
                    : "حساب کاربری شما"}
              </h1>
              <p>
                {tab === "dashboard"
                  ? "زمان حضورت را اعلام کن؛ ادامه هماهنگی با مدیر تیم."
                  : "اطلاعات و دسترسی‌ها را از اینجا مدیریت کنید."}
              </p>
            </div>
            <div className="title-mark">
              <CalendarDays size={30} />
            </div>
          </div>
          {error && (
            <div className="alert error" role="alert">
              {error}
              <button onClick={() => setError("")}>×</button>
            </div>
          )}
          {notice && (
            <div className="alert success" role="status">
              {notice}
              <button onClick={() => setNotice("")}>×</button>
            </div>
          )}
          {tab === "admin" ? (
            <Admin
              teams={teams}
              busy={busy}
              onSave={async (fn) => {
                setBusy(true);
                setError("");
                try {
                  await fn();
                  setTeams(await api<Team[]>("/teams"));
                  setNotice("اطلاعات با موفقیت ذخیره شد.");
                } catch (e) {
                  setError((e as Error).message);
                } finally {
                  setBusy(false);
                }
              }}
            />
          ) : tab === "account" ? (
            <PasswordForm
              busy={busy}
              onSave={(b) =>
                act(() => api("/me/password", "PUT", b), "رمز عبور تغییر کرد.")
              }
            />
          ) : (
            <>
              <section className="weekbar">
                <div className="week-date">
                  <span className="calendar-tile">
                    <CalendarDays />
                  </span>
                  <div>
                    <small>برنامه هفته انتخاب‌شده</small>
                    <strong>
                      {persian(date)} <span>و</span>{" "}
                      {persian(shift(date, 1), {
                        day: "numeric",
                        month: "long",
                      })}
                    </strong>
                  </div>
                </div>
                <div className="week-actions">
                  {user.role === "ADMIN" && (
                    <select
                      disabled={busy}
                      aria-label="تیم"
                      value={team}
                      onChange={(e) => setTeam(e.target.value)}
                    >
                      {teams.map((t) => (
                        <option value={t.id} key={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  )}
                  <button
                    disabled={busy}
                    aria-label="هفته قبل"
                    onClick={() => setDate(shift(date, -7))}
                  >
                    <ChevronRight size={18} />
                  </button>
                  <button
                    disabled={busy}
                    onClick={() => setDate(currentThursday())}
                  >
                    هفته جاری
                  </button>
                  <button
                    disabled={busy}
                    aria-label="هفته بعد"
                    onClick={() => setDate(shift(date, 7))}
                  >
                    <ChevronLeft size={18} />
                  </button>
                </div>
              </section>
              {loading ? (
                <div className="panel empty">در حال دریافت برنامه…</div>
              ) : (
                week && (
                  <>
                    <div className="stats">
                      <Stat
                        title={manager ? "اعضای تیم" : "درخواست من"}
                        value={
                          manager ? members.length : own?.requestId ? 1 : 0
                        }
                        detail={
                          manager ? "همکار در این تیم" : "برای هفته انتخاب‌شده"
                        }
                        icon={<Users />}
                      />
                      <Stat
                        title="حضور تأییدشده"
                        value={count("APPROVED")}
                        detail="نفر در حداقل یک روز"
                        icon={<Check />}
                        green
                      />
                      <Stat
                        title="در انتظار بررسی"
                        value={count("PENDING")}
                        detail="درخواست منتظر تصمیم"
                        icon={<Clock />}
                      />
                      <Stat
                        title="اعلام‌نشده"
                        value={count("UNDECLARED")}
                        detail="هنوز برنامه‌ای ثبت نشده"
                        icon={<CalendarDays />}
                      />
                    </div>
                    {user.role !== "ADMIN" && (
                      <RequestCard
                        key={date + JSON.stringify(own) + locked}
                        own={own}
                        date={date}
                        locked={locked}
                        busy={busy}
                        onSave={(body) =>
                          act(
                            () => api(`/me/weeks/${date}`, "PUT", body),
                            "برنامه حضور شما ثبت شد.",
                          )
                        }
                      />
                    )}
                    {manager && locked && (
                      <section className="print-only">
                        <h1>فهرست حضور تأییدشده</h1>
                        <p>
                          {selectedTeam?.name} · {persian(date)} و{" "}
                          {persian(shift(date, 1))}
                        </p>
                        <table>
                          <thead>
                            <tr>
                              <th>نام</th>
                              <th>کد پرسنلی</th>
                              <th>پنجشنبه</th>
                              <th>جمعه</th>
                            </tr>
                          </thead>
                          <tbody>
                            {members
                              .filter(
                                (m) =>
                                  m.thursdayStatus === "APPROVED" ||
                                  m.fridayStatus === "APPROVED",
                              )
                              .map((m) => (
                                <tr key={m.userId}>
                                  <td>{m.fullName}</td>
                                  <td>{m.personnelCode}</td>
                                  <td>
                                    {m.thursdayStatus === "APPROVED"
                                      ? "حضور"
                                      : "—"}
                                  </td>
                                  <td>
                                    {m.fridayStatus === "APPROVED"
                                      ? "حضور"
                                      : "—"}
                                  </td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                        <p>شناسه فهرست: {week.id}</p>
                      </section>
                    )}
                    <section className="panel roster">
                      <div className="section-head">
                        <div>
                          <h2>
                            {manager ? "فهرست حضور تیم" : "وضعیت درخواست شما"}
                          </h2>
                          <p>
                            پنجشنبه و جمعه ·{" "}
                            {locked
                              ? "فهرست نهایی شده است"
                              : "درخواست‌ها تا پیش از بررسی قابل ویرایش هستند"}
                          </p>
                        </div>
                        <span className={"phase " + (locked ? "done" : "")}>
                          {locked ? (
                            <>
                              <LockKeyhole size={14} />
                              نهایی شده
                            </>
                          ) : (
                            <>ثبت و بررسی</>
                          )}
                        </span>
                      </div>
                      <div className="table-scroll">
                        <table>
                          <thead>
                            <tr>
                              <th>همکار</th>
                              <th>پنجشنبه</th>
                              <th>جمعه</th>
                              <th>توضیحات</th>
                              {manager && <th>بررسی درخواست</th>}
                            </tr>
                          </thead>
                          <tbody>
                            {members.map((m) => (
                              <MemberRow
                                key={m.userId + JSON.stringify(m)}
                                member={m}
                                manager={manager}
                                locked={locked}
                                busy={busy}
                                onReview={(b) =>
                                  act(
                                    () =>
                                      api(
                                        base + "/requests/" + m.requestId,
                                        "PUT",
                                        b,
                                      ),
                                    "تصمیم مدیر ثبت شد.",
                                  )
                                }
                              />
                            ))}
                          </tbody>
                        </table>
                        {!members.length && (
                          <div className="empty">
                            هنوز عضوی برای این تیم ثبت نشده است.
                          </div>
                        )}
                      </div>
                      <div className="table-footer">
                        <span>
                          {members.length.toLocaleString("fa-IR")} نفر · هفته{" "}
                          {persian(date)}
                        </span>
                        <span>تأیید مدیر، پیش‌نیاز هماهنگی ورود است.</span>
                      </div>
                    </section>
                    {manager && (
                      <section className="panel dispatch">
                        <div className="dispatch-icon">
                          <Mail />
                        </div>
                        <div className="dispatch-copy">
                          <h2>
                            {locked
                              ? "فهرست نهایی و هماهنگی ورود"
                              : "آماده ارسال برای هماهنگی؟"}
                          </h2>
                          <p>
                            {locked
                              ? "فهرست ثبت شده و درخواست ایمیل ذخیره شده است."
                              : "پس از بررسی همه درخواست‌ها، فهرست را نهایی کنید."}
                          </p>
                          <small>
                            گیرنده تنظیم‌شده:{" "}
                            <b dir="ltr">{selectedTeam?.coordinatorEmail}</b>
                          </small>
                          {week.mail && (
                            <p className="mail-state">
                              وضعیت ایمیل:{" "}
                              <strong>
                                {
                                  (
                                    {
                                      QUEUED: "در صف ارسال",
                                      SENT: "تحویل سرور ایمیل شد",
                                      FAILED: "ارسال ناموفق",
                                    } as Record<string, string>
                                  )[week.mail.status]
                                }
                              </strong>{" "}
                              · {week.mail.attempts.toLocaleString("fa-IR")}{" "}
                              تلاش{" "}
                              {week.mail.error && (
                                <span>{week.mail.error}</span>
                              )}
                            </p>
                          )}
                        </div>
                        <div className="dispatch-actions">
                          {locked ? (
                            <>
                              <a
                                className="button secondary"
                                href={"/api" + base + "/export.csv"}
                              >
                                <Download size={16} />
                                خروجی CSV
                              </a>
                              <button
                                className="secondary"
                                onClick={() => window.print()}
                              >
                                چاپ فهرست
                              </button>
                              {week.mail?.status === "FAILED" && (
                                <button
                                  disabled={busy}
                                  onClick={() =>
                                    act(
                                      () => api(base + "/retry-email", "POST"),
                                      "ایمیل دوباره در صف ارسال قرار گرفت.",
                                    )
                                  }
                                >
                                  تلاش مجدد ایمیل
                                </button>
                              )}
                            </>
                          ) : (
                            <button
                              className="primary"
                              disabled={
                                busy ||
                                count("PENDING") > 0 ||
                                count("APPROVED") === 0
                              }
                              onClick={() => setFinalConfirm(true)}
                            >
                              نهایی‌کردن و ارسال
                              <ArrowUpLeft size={17} />
                            </button>
                          )}
                        </div>
                      </section>
                    )}
                  </>
                )
              )}
            </>
          )}
          <footer>
            نگاه · همراه آخر هفته <span>هماهنگی حضور، با خیال راحت‌تر</span>
          </footer>
        </div>
      </main>
      {finalConfirm && (
        <div className="modal-backdrop">
          <section
            className="panel modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
          >
            <Mail size={28} />
            <h2 id="confirm-title">فهرست نهایی شود؟</h2>
            <p>
              پس از نهایی‌شدن، فهرست این هفته قفل می‌شود و ایمیل برای
              هماهنگ‌کننده در صف قرار می‌گیرد.
            </p>
            {count("UNDECLARED") > 0 && (
              <div className="alert">
                {count("UNDECLARED").toLocaleString("fa-IR")} نفر هنوز حضور خود
                را اعلام نکرده‌اند و در خروجی نخواهند بود.
              </div>
            )}
            <p dir="ltr">{selectedTeam?.coordinatorEmail}</p>
            <div className="form-actions">
              <button
                className="primary"
                disabled={busy}
                onClick={() => {
                  setFinalConfirm(false);
                  act(
                    () => api(base + "/finalize", "POST"),
                    "فهرست نهایی و ایمیل در صف قرار گرفت.",
                  );
                }}
              >
                تأیید و نهایی‌کردن
              </button>
              <button
                className="secondary"
                onClick={() => setFinalConfirm(false)}
              >
                بازگشت
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
function Stat({
  title,
  value,
  detail,
  icon,
  green = false,
}: {
  title: string;
  value: number;
  detail: string;
  icon: React.ReactNode;
  green?: boolean;
}) {
  return (
    <section className={"stat " + (green ? "green" : "")}>
      <div>
        <span>{title}</span>
        <strong>{value.toLocaleString("fa-IR")}</strong>
        <small>{detail}</small>
      </div>
      <div className="stat-icon">{icon}</div>
    </section>
  );
}
function Login({ onLogin }: { onLogin: () => Promise<void> }) {
  const [username, setUsername] = useState(""),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <div className="login-page">
      <div className="login-story">
        <div className="brand">
          <Leaf />
          <strong>نگاه</strong>
        </div>
        <div>
          <span className="eyebrow">کنار هم، هماهنگ‌تر</span>
          <h1>
            آخر هفته،
            <br />
            با یک برنامه روشن.
          </h1>
          <p>
            حضور خود را اعلام کنید، نتیجه بررسی را ببینید
            <br />و هماهنگی ورود را به تیم بسپارید.
          </p>
          <div className="story-days">
            <span>
              پنجشنبه <Check size={18} />
            </span>
            <span>
              جمعه <Check size={18} />
            </span>
          </div>
        </div>
        <small>NEGAH VOLUNTEER · 01</small>
      </div>
      <form
        className="login-form"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError("");
          try {
            await login(username, password);
            await onLogin();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="brand-icon">
          <Leaf />
        </div>
        <h2>خوش آمدید.</h2>
        <p>برای هماهنگی حضور، وارد حساب کاربری خود شوید.</p>
        {error && (
          <div className="alert error" role="alert">
            {error}
          </div>
        )}
        <label>
          نام کاربری
          <input
            required
            autoComplete="username"
            dir="ltr"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="username"
          />
        </label>
        <label>
          رمز عبور
          <input
            required
            type="password"
            autoComplete="current-password"
            dir="ltr"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••••••"
          />
        </label>
        <button className="primary" disabled={busy}>
          {busy ? "در حال ورود…" : "ورود به فضای کاری"}
          <ArrowUpLeft size={18} />
        </button>
        <small>حساب کاربری توسط مدیر سیستم ساخته می‌شود.</small>
      </form>
    </div>
  );
}
function RequestCard({
  own,
  date,
  locked,
  busy,
  onSave,
}: {
  own: Member | undefined;
  date: string;
  locked: boolean;
  busy: boolean;
  onSave: (b: unknown) => Promise<void>;
}) {
  const [th, setTh] = useState(
      !!own && ["PENDING", "APPROVED"].includes(own.thursdayStatus),
    ),
    [fr, setFr] = useState(
      !!own && ["PENDING", "APPROVED"].includes(own.fridayStatus),
    ),
    [note, setNote] = useState(own?.note || "");
  const disabled = locked || !!own?.reviewedBy || date < currentThursday();
  return (
    <section className="panel request">
      <div className="section-head">
        <div>
          <h2>این آخر هفته می‌آیی؟</h2>
          <p>
            {disabled
              ? "برنامه شما ثبت و قفل شده است."
              : "روزهای حضورت را انتخاب کن. انتخاب‌نکردن هر دو روز یعنی عدم حضور."}
          </p>
        </div>
        <span className="subtle">برنامه من</span>
      </div>
      <div className="request-body">
        <div className="day-options">
          {[
            { title: "پنجشنبه", date, checked: th, set: setTh },
            { title: "جمعه", date: shift(date, 1), checked: fr, set: setFr },
          ].map((d) => (
            <label
              className={"day " + (d.checked ? "selected" : "")}
              key={d.title}
            >
              <input
                type="checkbox"
                checked={d.checked}
                disabled={disabled || busy}
                onChange={(e) => d.set(e.target.checked)}
              />
              <span>
                <strong>{d.title}</strong>
                <small>
                  {persian(d.date, { day: "numeric", month: "long" })}
                </small>
              </span>
              <CalendarDays size={22} />
            </label>
          ))}
        </div>
        <label className="note-label">
          توضیحات اختیاری
          <input
            maxLength={500}
            value={note}
            disabled={disabled || busy}
            onChange={(e) => setNote(e.target.value)}
            placeholder="مثلاً: تکمیل نسخه جدید پروژه"
          />
        </label>
        <button
          className="primary"
          disabled={disabled || busy}
          onClick={() => onSave({ thursday: th, friday: fr, note })}
        >
          {own?.requestId ? "ذخیره تغییرات" : "ثبت برنامه حضور"}
          <Check size={17} />
        </button>
      </div>
    </section>
  );
}
function MemberRow({
  member: m,
  manager,
  locked,
  busy,
  onReview,
}: {
  member: Member;
  manager: boolean;
  locked: boolean;
  busy: boolean;
  onReview: (b: unknown) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false),
    [th, setTh] = useState(
      m.thursdayStatus === "PENDING" ? "APPROVED" : m.thursdayStatus,
    ),
    [fr, setFr] = useState(
      m.fridayStatus === "PENDING" ? "APPROVED" : m.fridayStatus,
    ),
    [note, setNote] = useState(m.reviewNote);
  const choice = (status: string, value: string, set: (s: string) => void) =>
    status === "NOT_ATTENDING" ? (
      <Badge status={status} />
    ) : (
      <select
        aria-label="تصمیم مدیر"
        value={value}
        onChange={(e) => set(e.target.value)}
      >
        <option value="APPROVED">تأیید</option>
        <option value="REJECTED">رد</option>
      </select>
    );
  return (
    <tr>
      <td>
        <div className="member">
          <span className="avatar">{m.fullName.slice(0, 1)}</span>
          <div>
            <strong>{m.fullName}</strong>
            <small>{m.personnelCode}</small>
          </div>
        </div>
      </td>
      <td>
        {editing ? (
          choice(m.thursdayStatus, th, setTh)
        ) : (
          <Badge status={m.thursdayStatus} />
        )}
      </td>
      <td>
        {editing ? (
          choice(m.fridayStatus, fr, setFr)
        ) : (
          <Badge status={m.fridayStatus} />
        )}
      </td>
      <td className="notes">
        {m.note || "—"}
        {m.reviewNote && <small>نظر مدیر: {m.reviewNote}</small>}
        {m.reviewedBy && <small>بررسی: {m.reviewedBy}</small>}
        {editing && (
          <input
            aria-label="توضیح مدیر"
            placeholder="توضیح مدیر"
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        )}
      </td>
      {manager && (
        <td>
          {editing ? (
            <div className="row-actions">
              <button
                className="primary compact"
                disabled={busy}
                onClick={async () => {
                  await onReview({ thursday: th, friday: fr, note });
                  setEditing(false);
                }}
              >
                ثبت
              </button>
              <button
                className="secondary compact"
                disabled={busy}
                onClick={() => setEditing(false)}
              >
                لغو
              </button>
            </div>
          ) : m.requestId &&
            !locked &&
            (m.thursdayStatus !== "NOT_ATTENDING" ||
              m.fridayStatus !== "NOT_ATTENDING") ? (
            <button
              disabled={busy}
              className="text-button"
              onClick={() => setEditing(true)}
            >
              {m.reviewedBy ? "اصلاح تصمیم" : "بررسی"}
              <ChevronLeft size={14} />
            </button>
          ) : (
            <span className="subtle">—</span>
          )}
        </td>
      )}
    </tr>
  );
}
function Admin({
  teams,
  busy,
  onSave,
}: {
  teams: Team[];
  busy: boolean;
  onSave: (fn: () => Promise<unknown>) => Promise<void>;
}) {
  const [name, setName] = useState(""),
    [email, setEmail] = useState(""),
    [editTeam, setEditTeam] = useState("");
  return (
    <div className="admin-grid">
      <form
        className="panel form-panel"
        onSubmit={(e) => {
          e.preventDefault();
          onSave(() =>
            api(
              editTeam ? "/teams/" + editTeam : "/teams",
              editTeam ? "PUT" : "POST",
              { name, coordinatorEmail: email },
            ),
          );
        }}
      >
        <h2>تیم و هماهنگ‌کننده</h2>
        <label>
          تیم
          <select
            value={editTeam}
            onChange={(e) => {
              setEditTeam(e.target.value);
              const t = teams.find((t) => t.id === e.target.value);
              setName(t?.name || "");
              setEmail(t?.coordinatorEmail || "");
            }}
          >
            <option value="">ایجاد تیم جدید</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          نام تیم
          <input
            required
            maxLength={100}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label>
          ایمیل هماهنگ‌کننده
          <input
            required
            type="email"
            dir="ltr"
            maxLength={254}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <button disabled={busy} className="primary">
          <Plus size={17} />
          {editTeam ? "ذخیره تیم" : "ساخت تیم"}
        </button>
        <small>
          تغییر گیرنده روی ایمیل فهرست‌های قبلاً نهایی‌شده اثر ندارد.
        </small>
      </form>
      <form
        className="panel form-panel"
        onSubmit={(e) => {
          e.preventDefault();
          const f = e.currentTarget;
          const b = Object.fromEntries(new FormData(f));
          onSave(() => api("/users", "POST", b));
        }}
      >
        <h2>افزودن همکار</h2>
        <label>
          نام و نام خانوادگی
          <input name="fullName" required maxLength={100} />
        </label>
        <div className="two-fields">
          <label>
            نام کاربری
            <input
              name="username"
              dir="ltr"
              pattern="[a-zA-Z0-9._-]{3,80}"
              required
            />
          </label>
          <label>
            کد پرسنلی
            <input name="personnelCode" required maxLength={40} />
          </label>
        </div>
        <label>
          رمز اولیه
          <input
            name="password"
            type="password"
            autoComplete="new-password"
            minLength={12}
            maxLength={64}
            required
          />
        </label>
        <div className="two-fields">
          <label>
            نقش
            <select name="role">
              <option value="EMPLOYEE">عضو تیم</option>
              <option value="MANAGER">مدیر تیم</option>
              <option value="ADMIN">مدیر سیستم</option>
            </select>
          </label>
          <label>
            تیم
            <select name="teamId" required>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button disabled={busy} className="primary">
          <Plus size={17} />
          افزودن عضو
        </button>
      </form>
    </div>
  );
}
function PasswordForm({
  busy,
  onSave,
}: {
  busy: boolean;
  onSave: (b: unknown) => Promise<void>;
}) {
  return (
    <form
      className="panel form-panel password-panel"
      onSubmit={(e) => {
        e.preventDefault();
        onSave(Object.fromEntries(new FormData(e.currentTarget)));
      }}
    >
      <h2>تغییر رمز عبور</h2>
      <label>
        رمز فعلی
        <input
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          required
        />
      </label>
      <label>
        رمز جدید
        <input
          name="newPassword"
          type="password"
          autoComplete="new-password"
          minLength={12}
          maxLength={64}
          required
        />
      </label>
      <button className="primary" disabled={busy}>
        ذخیره رمز جدید
      </button>
    </form>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
