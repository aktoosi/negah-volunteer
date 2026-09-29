let csrf: { token: string; headerName: string } | null = null;
export async function refreshCsrf() {
  const r = await fetch("/api/csrf", { credentials: "same-origin" });
  if (!r.ok) throw new Error("ارتباط با سرور برقرار نشد");
  csrf = await r.json();
}
export async function api<T = void>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  if (method !== "GET" && !csrf) await refreshCsrf();
  const headers: Record<string, string> = {};
  if (method !== "GET" && csrf) headers[csrf.headerName] = csrf.token;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch("/api" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: "same-origin",
  });
  if (!res.ok) {
    let message =
      res.status === 401
        ? "لطفاً دوباره وارد شوید"
        : res.status === 403
          ? "دسترسی ندارید یا نشست منقضی شده است"
          : "انجام عملیات ممکن نشد";
    try {
      const b = await res.json();
      message = b.message || message;
    } catch {}
    throw new Error(message);
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
export async function login(username: string, password: string) {
  await refreshCsrf();
  const res = await fetch("/api/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      [csrf!.headerName]: csrf!.token,
    },
    body: new URLSearchParams({ username, password }),
    credentials: "same-origin",
  });
  if (!res.ok) throw new Error("نام کاربری یا رمز عبور صحیح نیست");
  await refreshCsrf();
}
export type Person = {
  id: string;
  username: string;
  fullName: string;
  personnelCode: string;
  role: string;
  teamId: string;
};
export type Team = { id: string; name: string; coordinatorEmail: string };
export type Member = {
  userId: string;
  fullName: string;
  personnelCode: string;
  requestId: string | null;
  thursdayStatus: string;
  fridayStatus: string;
  note: string;
  reviewNote: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
};
export type Week = {
  id: string | null;
  thursday: string;
  status: string;
  members: Member[];
  mail: { status: string; attempts: number; error: string | null } | null;
};
