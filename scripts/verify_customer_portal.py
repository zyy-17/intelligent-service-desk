import urllib.request, urllib.error, json, time, sys

BASE = "http://localhost:8080/api"


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        r = urllib.request.urlopen(req, timeout=20)
        return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:200]}


def login(u, p):
    s, b = call("POST", "/auth/login", {"username": u, "password": p})
    d = b.get("data") or {}
    return d.get("token"), d, s, b


print("=== 1. 客户登录：菜单与数据隔离 ===")
tok_c, dc, sc, bc = login("customer1", "123456")
if not tok_c:
    print("  客户登录失败:", sc, bc.get("message"))
else:
    print("  role :", dc.get("user", {}).get("role"))
    print("  menus:", [(m.get("label"), m.get("path")) for m in dc.get("menus", [])])
    s, b = call("GET", "/tickets?page=1&pageSize=50", token=tok_c)
    d = b.get("data") or {}
    rows = d.get("list") or []
    names = sorted({(t.get("customer_name") or t.get("customerName") or "?") for t in rows})
    print("  工单 total:", d.get("total"), "| 当前页", len(rows), "条")
    print("  可见客户:", names)
    s2, b2 = call("GET", "/stats/my-overview", token=tok_c)
    print("  我的概览:", {k: (b2.get("data") or {}).get(k) for k in ("total", "processing", "waiting", "resolved")})

print()
print("=== 2. 管理员登录：对照数据量 ===")
tok_a, da, sa, ba = login("admin", "123456")
if not tok_a:
    print("  管理员登录失败:", sa, ba.get("message"))
else:
    print("  menus:", [(m.get("label"), m.get("path")) for m in da.get("menus", [])])
    s, b = call("GET", "/tickets?page=1&pageSize=1", token=tok_a)
    print("  全站工单 total:", (b.get("data") or {}).get("total"))

print()
print("=== 3. 客户写知识库应被拒绝 ===")
if tok_c:
    s, b = call("POST", "/knowledge", {"title": "测试越权", "body": "x"}, token=tok_c)
    print("  客户 POST /knowledge ->", s, "code:", b.get("code"), "msg:", b.get("message"))
    s, b = call("DELETE", "/knowledge/999999", token=tok_c)
    print("  客户 DELETE /knowledge/999999 ->", s, "code:", b.get("code"), "msg:", b.get("message"))

print()
print("=== 4. 客户读知识库应成功 ===")
if tok_c:
    s, b = call("GET", "/knowledge/hot?limit=3", token=tok_c)
    rows = b.get("data") or []
    print("  热门文章:", s, "|", [a.get("title") for a in rows[:3]])
    s, b = call("GET", "/knowledge?page=1&pageSize=3", token=tok_c)
    print("  列表 total:", (b.get("data") or {}).get("total"))

print()
print("=== 5. 客户访问他人工单详情应 403 ===")
if tok_c and tok_a:
    s, b = call("GET", "/tickets?page=1&pageSize=100", token=tok_c)
    mine = {t.get("id") for t in ((b.get("data") or {}).get("list") or [])}
    s, b = call("GET", "/tickets?page=1&pageSize=100", token=tok_a)
    all_rows = (b.get("data") or {}).get("list") or []
    others = [t.get("id") for t in all_rows if t.get("id") not in mine]
    if others:
        oid = others[0]
        s, b = call("GET", f"/tickets/{oid}", token=tok_c)
        print(f"  客户访问他人工单 {oid} ->", s, "code:", b.get("code"), "msg:", b.get("message"))
    else:
        print("  未取到他人工单样本（跳过）")

print()
print("=== 6. 分类接口（提交工单表单用） ===")
if tok_c:
    s, b = call("GET", "/tickets/meta/categories", token=tok_c)
    print("  ->", s, [c.get("name") for c in (b.get("data") or [])])
