import curses
import json
import requests
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo

API_BASE  = "http://localhost:10000/servlets/detoxrcm/v6.1.0"

DEFAULT_PROJECT = "detox"

MOCK_TABLE = "poc_note"
MOCK_SOURCE = "tui-client"

state = {
    "token": None,
    "email": None,
    "user_id": None,
    "project": DEFAULT_PROJECT,
    "local_notes": {},
    "pending_actions": [],
    "sync_progress": None,
}

# ---------- helpers ----------

def iso_to_epoch_millis(iso):
    return int(datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp() * 1000)


def now_millis():
    return int(datetime.utcnow().timestamp() * 1000)

def auth_headers(): 
    if state["token"]:
        return {"X-Auth-Token": state["token"]}
    return {}

# ---------- UI helpers ----------

def format_response_for_display(response):
    """
    Pretty-print JSON responses.
    Fallback to plain text if not JSON.
    """

    try:
        # Only attempt JSON if server says it's JSON
        content_type = response.headers.get("Content-Type", "")
        
        if "application/json" in content_type:
            parsed = response.json()
            pretty = json.dumps(parsed, indent=2, ensure_ascii=False)
            return pretty.splitlines()

        # Fallback: just show text
        return response.text.splitlines()

    except Exception:
        # Absolute fallback (never crash UI)
        return response.text.splitlines()


def draw_menu(stdscr, title, items, selected):
    stdscr.clear()
    stdscr.addstr(0, 2, title, curses.A_BOLD)
    stdscr.addstr(1, 2, f"API: {API_BASE}")
    stdscr.addstr(2, 2, f"Token: {'SET' if state['token'] else 'none'}")
    for i, item in enumerate(items):
        attr = curses.A_REVERSE if i == selected else curses.A_NORMAL
        stdscr.addstr(4 + i, 4, item, attr)
    stdscr.refresh()

def prompt(stdscr, y, label, secret=False):
    stdscr.addstr(y, 4, label)
    stdscr.refresh()
    curses.echo(not secret)
    value = stdscr.getstr(y, 4 + len(label) + 1).decode()
    curses.noecho()
    return value

def show_message(stdscr, lines):
    """
    Adaptive scrollable message viewer.
    If content fits screen → no scroll mode.
    If content exceeds screen → enable scrolling.
    """

    scroll = 0

    while True:
        stdscr.clear()
        h, w = stdscr.getmaxyx()

        total_lines = len(lines)
        needs_scroll = total_lines > h - 1

        if needs_scroll:
            visible_height = h - 2  # reserve footer
            max_scroll = total_lines - visible_height
            scroll = max(0, min(scroll, max_scroll))
        else:
            visible_height = total_lines
            max_scroll = 0
            scroll = 0

        # Draw content
        for i in range(visible_height):
            line_index = scroll + i
            if line_index >= total_lines:
                break

            safe_line = lines[line_index][:w - 2]
            stdscr.addstr(i, 1, safe_line)

        # Only show footer if scrolling is needed
        if needs_scroll:
            footer = f"Scroll: {scroll}/{max_scroll}  (↑ ↓ PgUp PgDn, q to exit)"
            stdscr.addstr(h - 1, 1, footer[:w - 2])
        else:
            stdscr.addstr(h - 1, 1, "Press any key to continue"[:w - 2])

        stdscr.refresh()

        key = stdscr.getch()

        if not needs_scroll:
            break  # just exit on any key

        if key == curses.KEY_UP:
            scroll -= 1
        elif key == curses.KEY_DOWN:
            scroll += 1
        elif key == curses.KEY_NPAGE:  # Page Down
            scroll += visible_height
        elif key == curses.KEY_PPAGE:  # Page Up
            scroll -= visible_height
        elif key in (ord('q'), 27):
            break


def show_response(stdscr, response):
    stdscr.clear()
    stdscr.addstr(0, 2, "Response", curses.A_BOLD)
    stdscr.addstr(1, 2, f"Status: {response.status_code}")
    for i, line in enumerate(response.text.splitlines()[:curses.LINES - 4]):
        stdscr.addstr(3 + i, 2, line)
    stdscr.addstr(curses.LINES - 1, 2, "Press any key to continue")
    stdscr.getch()

# ---------- Auth ----------

def create_account(stdscr):
    stdscr.clear()
    email = prompt(stdscr, 2, "Email:")
    password = prompt(stdscr, 3, "Password:", secret=True)
    project = prompt(stdscr, 4, "Project:")
    resp = requests.post(
        f"{API_BASE}/auth/signup",
        json={"email": email, "password": password, "project": project},
    )
    show_response(stdscr, resp)

def login(stdscr):
    stdscr.clear()
    email = prompt(stdscr, 2, "Email:")
    password = prompt(stdscr, 3, "Password:", secret=True)
    _login_common(stdscr, email, password)

def login_std(stdscr):
    _login_common(stdscr, "hi@bye.com", "123456")

def _login_common(stdscr, email, password):
    resp = requests.post(
        f"{API_BASE}/auth/login",
        json={"email": email, "password": password},
    )

    # Try to parse JSON for login logic
    try:
        data = resp.json()
    except Exception:
        data = {}

    # Display response (pretty JSON or fallback)
    lines = format_response_for_display(resp)

    # Handle login success
    if resp.status_code == 200 and data.get("token") and data.get("user"):
        state["token"] = data["token"].strip()
        state["email"] = email
        state["user_id"] = data["user"]

        lines = ["Login successful", ""] + lines
    else:
        lines = ["Login failed", ""] + lines

    show_message(stdscr, lines)


def logout(stdscr):
    resp = requests.get(
        f"{API_BASE}/auth/logout",
        headers=auth_headers(),
    )
    state.update({
        "token": None,
        "email": None,
        "user_id": None,
        "local_notes": {},
        "pending_actions": [],
        "sync_progress": None,
    })
    show_response(stdscr, resp)

def change_password(stdscr):
    old_pw = prompt(stdscr, 2, "Old password:", secret=True)
    new_pw = prompt(stdscr, 3, "New password:", secret=True)
    resp = requests.post(
        f"{API_BASE}/auth/change-password",
        json={"oldPassword": old_pw, "newPassword": new_pw},
        headers=auth_headers(),
    )
    show_response(stdscr, resp)

# ---------- API ----------

def list_projects(stdscr):
    resp = requests.get(
        f"{API_BASE}/project/list",
        headers=auth_headers(),
    )
    show_response(stdscr, resp)

def request_verify_email(stdscr):
    resp = requests.get(
        f"{API_BASE}/auth/request-verify-email",
        headers=auth_headers(),
    )
    show_response(stdscr, resp)

def show_token(stdscr):
    show_message(stdscr, [state["token"] or "No token"])

def raw_request(stdscr):
    stdscr.clear()
    method = prompt(stdscr, 2, "Method:")
    path = prompt(stdscr, 3, "Path:")
    body = prompt(stdscr, 4, "JSON body:")
    data = json.loads(body) if body.strip() else None
    resp = requests.request(
        method.upper(),
        f"{API_BASE}{path}",
        json=data,
        headers=auth_headers(),
    )
    show_response(stdscr, resp)

def get_detox_queue(stdscr):
    # Fetch all entries from DetoxMessageQueue table
    # and print them with spacing between records.

    if not state.get("user_id") or not state.get("token"):
        show_message(stdscr, ["Not logged in / no token"])
        return

    url = f"{API_BASE}/project/{state['project']}/table/detox_message_queue"
    params = {"user": state["user_id"]}

    try:
        resp = requests.get(url, headers=auth_headers(), params=params)
    except Exception as e:
        show_message(stdscr, [f"Request failed: {e}"])
        return

    lines = [
        "READ DetoxMessageQueue",
        f"Status: {resp.status_code}",
        ""
    ]

    if resp.status_code == 200:
        try:
            records = resp.json()

            for i, record in enumerate(records):
                lines.append(f"Entry {i+1}")
                for key, value in record.items():
                    lines.append(f"  {key}: {value}")
                lines.append("")  # <-- blank line between entries

        except Exception:
            lines += resp.text.splitlines()
    else:
        lines += resp.text.splitlines()

    show_message(stdscr, lines)


def send_measurement(stdscr):
    """
    Send heart rate, blood pressure, dagstart, or afwijkingsbeoordeling
    to detox_message_queue.
    """

    if not state.get("user_id") or not state.get("token"):
        show_message(stdscr, ["Not logged in / no token"])
        return

    items = [
        "Heart rate",
        "Blood pressure",
        "Dagstart",
        "Afwijkingsbeoordeling",
        "Back",
    ]
    actions = [
        _send_heartrate,
        _send_bloodpressure,
        _send_dagstart,
        _send_afwijkingsbeoordeling,
        None,
    ]
    selected = 0
    while True:
        draw_menu(stdscr, "Send Measurement", items, selected)
        key = stdscr.getch()
        if key == curses.KEY_UP and selected > 0:
            selected -= 1
        elif key == curses.KEY_DOWN and selected < len(items) - 1:
            selected += 1
        elif key in (10, 13):
            if actions[selected] is None:
                return
            actions[selected](stdscr)


def _post_to_queue(stdscr, payload_obj):
    """Helper: POST a single payload to detox_message_queue and show result."""
    url = f"{API_BASE}/project/{state['project']}/table/detox_message_queue"
    try:
        resp = requests.post(url, json=[payload_obj], headers=auth_headers())
    except Exception as e:
        show_message(stdscr, [f"Request failed: {e}"])
        return

    sent_json = json.dumps(payload_obj, indent=2)
    lines = (
        ["Sent message:", ""]
        + sent_json.splitlines()
        + ["", "Server response:"]
        + format_response_for_display(resp)
    )
    show_message(stdscr, lines)


def _send_heartrate(stdscr):
    curses.echo()
    stdscr.clear()
    stdscr.addstr(0, 0, "Enter heart rate value: ")
    value_raw = stdscr.getstr(1, 0).decode().strip()
    stdscr.addstr(3, 0, "Enter optional comment: ")
    comment = stdscr.getstr(4, 0).decode().strip()
    curses.noecho()

    try:
        value = int(value_raw)
    except ValueError:
        show_message(stdscr, ["Invalid numeric input"])
        return

    now = datetime.now()
    payload = {
        "type": "heartrate",
        "value": value,
        "comment": comment,
        "timestampUtcMillis": int(now.timestamp() * 1000),
        "timeZone": "Europe/Amsterdam",
        "localTime": now.isoformat(),
    }
    _post_to_queue(stdscr, payload)


def _send_bloodpressure(stdscr):
    curses.echo()
    stdscr.clear()
    stdscr.addstr(0, 0, "Enter systolic: ")
    systolic_raw = stdscr.getstr(1, 0).decode().strip()
    stdscr.addstr(3, 0, "Enter diastolic: ")
    diastolic_raw = stdscr.getstr(4, 0).decode().strip()
    stdscr.addstr(6, 0, "Enter optional comment: ")
    comment = stdscr.getstr(7, 0).decode().strip()
    curses.noecho()

    try:
        systolic = int(systolic_raw)
        diastolic = int(diastolic_raw)
        map_val = int((2 * diastolic + systolic) / 3)
    except ValueError:
        show_message(stdscr, ["Invalid numeric input"])
        return

    now = datetime.now()
    payload = {
        "type": "bloodpressure",
        "systolic": systolic,
        "diastolic": diastolic,
        "meanArterialPressure": map_val,
        "comment": comment,
        "timestampUtcMillis": int(now.timestamp() * 1000),
        "timeZone": "Europe/Amsterdam",
        "localTime": now.isoformat(),
    }
    _post_to_queue(stdscr, payload)


def _send_dagstart(stdscr):
    curses.echo()
    stdscr.clear()
    stdscr.addstr(0, 0, "Enter how ya feelz: ")
    gmg = stdscr.getstr(1, 0).decode().strip()
    stdscr.addstr(3, 0, "Enter if je vertrouwen hebt: ")
    vrt = stdscr.getstr(4, 0).decode().strip()
    curses.noecho()

    now = datetime.now()
    payload = {
        "type": "detox_dagstart",
        "timestampUtcMillis": int(now.timestamp() * 1000),
        "hoeGaatHetVanochtend": gmg,
        "hebJeVertrouwenInVandaag": vrt,
        "wilJeVandaagContactMetJouwHulpverlener": False,
    }
    _post_to_queue(stdscr, payload)


def _send_afwijkingsbeoordeling(stdscr):
    """
    Send an 'afwijkingsbeoordeling' record to detox_message_queue.
    """

    # ---- answer-code tables ----
    OV_OPTIONS = [
        ("at1", "Nee, ik heb me aan de afspraken gehouden"),
        ("at2", "Ik heb een sigaret gerookt"),
        ("at3", "Ik heb alcohol gedronken of drugs gebruikt"),
    ]
    GEBRUIK_OPTIONS = [
        ("at14", "Ik heb een klein beetje gebruikt"),
        ("at15", "Ik heb matig gebruikt"),
        ("at16", "Ik heb veel gebruikt"),
    ]
    ALLEEN_OPTIONS = [
        ("at19", "Alleen"),
        ("at20", "Met vrienden of bekenden"),
        ("at21", "Op een feestje of evenement"),
    ]
    SNELHEID_OPTIONS = [
        ("at26", "Zo snel mogelijk (noodgeval)"),
        ("at27", "Vandaag nog"),
        ("at28", "Binnen een paar dagen"),
        ("at29", "Het is niet dringend"),
    ]

    def pick_option(row, label, options):
        stdscr.addstr(row, 0, label)
        for i, (code, desc) in enumerate(options):
            stdscr.addstr(row + 1 + i, 2, f"{i + 1}. [{code}] {desc}")
        prompt_row = row + 1 + len(options)
        stdscr.addstr(prompt_row, 2, "Keuze: ")
        stdscr.refresh()
        raw = stdscr.getstr(prompt_row, 9).decode().strip()
        try:
            idx = int(raw) - 1
            if 0 <= idx < len(options):
                return options[idx][0], prompt_row + 2
        except ValueError:
            pass
        return None, prompt_row + 2

    curses.echo()
    stdscr.clear()
    stdscr.addstr(0, 0, "=== Afwijkingsbeoordeling ===")
    row = 2

    openingsvraag, row = pick_option(row, "Openingsvraag:", OV_OPTIONS)
    if openingsvraag is None:
        curses.noecho()
        show_message(stdscr, ["Ongeldige keuze, afgebroken"])
        return

    gebruik = None
    alleen_of_niet = None
    hoe_snel_hulp = None
    heb_je_hulp_nodig = False
    waar_hulp = ""

    if openingsvraag == "at3":
        gebruik, row = pick_option(row, "Hoeveel gebruik:", GEBRUIK_OPTIONS)
        if gebruik is None:
            curses.noecho()
            show_message(stdscr, ["Ongeldige keuze, afgebroken"])
            return

        alleen_of_niet, row = pick_option(row, "Alleen of met anderen:", ALLEEN_OPTIONS)
        if alleen_of_niet is None:
            curses.noecho()
            show_message(stdscr, ["Ongeldige keuze, afgebroken"])
            return

        stdscr.addstr(row, 0, "Heb je hulp nodig? (j/n): ")
        stdscr.refresh()
        hulp_raw = stdscr.getstr(row, 26).decode().strip().lower()
        heb_je_hulp_nodig = hulp_raw == "j"
        row += 2

        if heb_je_hulp_nodig:
            stdscr.addstr(row, 0, "Waar heb je hulp bij nodig?: ")
            stdscr.refresh()
            waar_hulp = stdscr.getstr(row, 29).decode().strip()
            row += 2

            hoe_snel_hulp, row = pick_option(row, "Hoe snel hulp nodig:", SNELHEID_OPTIONS)
            if hoe_snel_hulp is None:
                curses.noecho()
                show_message(stdscr, ["Ongeldige keuze, afgebroken"])
                return

    curses.noecho()

    now = datetime.now()
    now_utc_ms = int(now.timestamp() * 1000)

    outer = {
        "user": state["user_id"],
        "type": "afwijkingsbeoordeling",
        "openingsvraag": openingsvraag,
        "hebJeHulpNodig": heb_je_hulp_nodig,
        "timestampUtcMillis": now_utc_ms,
        "timeZone": "Europe/Amsterdam",
    }
    if waar_hulp:
        outer["waarHebJeHulpBijNodig"] = waar_hulp
    if gebruik:
        outer["gebruik"] = gebruik
    if alleen_of_niet:
        outer["alleenOfNiet"] = alleen_of_niet
    if hoe_snel_hulp:
        outer["hoeSnelHulp"] = hoe_snel_hulp

    _post_to_queue(stdscr, outer)


# ---------- Menus ----------

def sync_menu(stdscr):
    items = [
        "Send measurement",
        "Read remote database",
        "Back",
    ]
    actions = [
        send_measurement,
        get_detox_queue,
        None,
    ]
    selected = 0
    while True:
        draw_menu(stdscr, "Sync Menu", items, selected)
        key = stdscr.getch()
        if key == curses.KEY_UP and selected > 0:
            selected -= 1
        elif key == curses.KEY_DOWN and selected < len(items) - 1:
            selected += 1
        elif key in (10, 13):
            if actions[selected] is None:
                return
            actions[selected](stdscr)


def get_user_profile(stdscr):
    """
    Fetch and display the full profile of the current user.
    """

    if not state.get("user_id") or not state.get("token"):
        show_message(stdscr, ["Not logged in"])
        return

    user_id = state["user_id"]
    url = f"{API_BASE}/user/?user={user_id}"

    try:
        resp = requests.get(url, headers=auth_headers())
    except Exception as e:
        show_message(stdscr, [f"Request failed: {e}"])
        return

    lines = [
        "User Profile",
        "",
        f"User ID: {user_id}",
        f"Status: {resp.status_code}",
        "",
    ]

    try:
        data = resp.json()
        lines += json.dumps(data, indent=2).splitlines()
    except Exception:
        lines += resp.text.splitlines()

    show_message(stdscr, lines)


def set_user_role(stdscr):
    """
    Set the role of another user (admin only).
    PUT /user/role?user=<userid>&role=<PATIENT|PROFESSIONAL|ADMIN>
    """

    if not state.get("token"):
        show_message(stdscr, ["Not logged in / no token"])
        return

    curses.echo()
    stdscr.clear()

    stdscr.addstr(0, 0, "Enter target user ID: ")
    target_user = stdscr.getstr(1, 0).decode().strip()

    stdscr.addstr(3, 0, "Select role:")
    stdscr.addstr(4, 0, "  1 = PATIENT")
    stdscr.addstr(5, 0, "  2 = PROFESSIONAL")
    stdscr.addstr(6, 0, "  3 = ADMIN")
    stdscr.addstr(7, 0, "Choice: ")
    choice = stdscr.getstr(8, 0).decode().strip()

    curses.noecho()

    roles = {"1": "PATIENT", "2": "PROFESSIONAL", "3": "ADMIN"}
    role = roles.get(choice)

    if not role:
        show_message(stdscr, ["Invalid choice, aborting"])
        return

    url = f"{API_BASE}/user/role"
    params = {"user": target_user, "role": role}

    try:
        resp = requests.put(url, headers=auth_headers(), params=params)
    except Exception as e:
        show_message(stdscr, [f"Request failed: {e}"])
        return

    lines = [
        "Set User Role",
        "",
        f"User ID : {target_user}",
        f"Role    : {role}",
        f"Status  : {resp.status_code}",
        "",
    ]

    if resp.text.strip():
        lines += format_response_for_display(resp)
    else:
        lines.append("(no response body)" if resp.status_code != 200 else "OK")

    show_message(stdscr, lines)


def detox_ons_signup(stdscr):
    """
    Call ONS signup endpoint and display returned QR code with debug info.
    """

    if not state.get("token"):
        show_message(stdscr, ["Not logged in / no token"])
        return

    curses.echo()
    stdscr.clear()

    stdscr.addstr(0, 0, "Enter ONS ID: ")
    ons_id = stdscr.getstr(1, 0).decode().strip()

    curses.noecho()

    url = f"{API_BASE}/user/detox/ons-signup?onsId={ons_id}&project={state['project']}"

    try:
        resp = requests.post(url, headers=auth_headers())
    except Exception as e:
        show_message(stdscr, [f"Request failed: {e}"])
        return

    lines = [
        "ONS Signup Request",
        "",
        f"URL: {url}",
        f"Status: {resp.status_code}",
        "",
        "Response:",
    ]

    try:
        data = resp.json()
        lines += json.dumps(data, indent=2).splitlines()
    except Exception:
        lines += resp.text.splitlines()
        show_message(stdscr, lines)
        return

    lines.append("")

    qr_payload = data.get("qrPayload")

    if qr_payload:
        lines += ["QR Code:", ""]
        try:
            lines += qr_to_ascii(qr_payload)
        except Exception as e:
            lines += [f"QR render failed: {e}"]
    else:
        lines += ["No qrPayload returned"]

    show_message(stdscr, lines)


def qr_to_ascii(payload):
    import qrcode

    qr = qrcode.QRCode(border=1)
    qr.add_data(payload)
    qr.make(fit=True)

    matrix = qr.get_matrix()

    lines = []
    for row in matrix:
        line = "".join("██" if cell else "  " for cell in row)
        lines.append(line)

    return lines


def account_menu(stdscr):
    items = [
        "Create account",
        "Change password",
        "Link phone (ONS signup)",
        "List projects",
        "Verify email",
        "Show token",
        "Show profile",
        "Set user role",
        "Back",
    ]
    actions = [
        create_account,
        change_password,
        detox_ons_signup,
        list_projects,
        request_verify_email,
        show_token,
        get_user_profile,
        set_user_role,
        None,
    ]
    selected = 0
    while True:
        draw_menu(stdscr, "Account Menu", items, selected)
        key = stdscr.getch()
        if key == curses.KEY_UP and selected > 0:
            selected -= 1
        elif key == curses.KEY_DOWN and selected < len(items) - 1:
            selected += 1
        elif key in (10, 13):
            if actions[selected] is None:
                return
            actions[selected](stdscr)


def main(stdscr):
    curses.curs_set(0)
    items = [
        "Login standard user",
        "Login",
        "Logout",
        "Sync >",
        "Account >",
        "Raw API request",
        "Quit",
    ]
    actions = [
        login_std,
        login,
        logout,
        sync_menu,
        account_menu,
        raw_request,
        None,
    ]
    selected = 0
    while True:
        draw_menu(stdscr, "SenSeeAct Sync TUI", items, selected)
        key = stdscr.getch()
        if key == curses.KEY_UP and selected > 0:
            selected -= 1
        elif key == curses.KEY_DOWN and selected < len(items) - 1:
            selected += 1
        elif key in (10, 13):
            if actions[selected] is None:
                break
            actions[selected](stdscr)

if __name__ == "__main__":
    curses.wrapper(main)