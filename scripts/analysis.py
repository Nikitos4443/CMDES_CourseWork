import collections
import csv
import math
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "results"

EPS_T = 0.001       # точність оцінки T, років
SHARE_EPS = 0.005   # точність для перехідного періоду (частка від p)


def rows(name):
    with open(RES / f"{name}.csv", encoding="utf8") as f:
        return list(csv.DictReader(f))


def save(name, header, data):
    with open(RES / f"derived_{name}.csv", "w", encoding="utf8", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(data)

    print(f"\n== {name} ==")
    print(*header, sep="\t")
    for r in data:
        print(*r, sep="\t")


# кількість замірів, формула (4.2)
def replications():
    data = []

    for r in rows("replications"):
        sigma = float(r["stdT"])
        need = max(1, math.ceil(20 * (sigma / EPS_T) ** 2))
        data.append([r["n"], f"{sigma:.7f}", need])

    save("replications", ["n", "stdT", "r"], data)


# перехідний період, формула (4.1)
def transient():
    by_day = collections.defaultdict(lambda: collections.defaultdict(list))
    for x in rows("transient"):
        by_day[x["n"]][int(x["days"])].append(float(x["profitPerYear"]))

    data = []
    for n, days in by_day.items():
        order = sorted(days)
        last = days[order[-1]]
        final = sum(last) / len(last)

        for share in (SHARE_EPS, 0.001):
            eps = share * final
            found = None

            for d in reversed(order):
                if max(days[d]) - min(days[d]) < eps:
                    found = d
                else:
                    break

            data.append([n, f"{share * 100:g}", round(eps), found if found else "не досягнуто"])

    save("transient", ["n", "eps_percent", "eps_uah", "days"], data)


# таблиця 4.1
def warmup():
    groups = collections.defaultdict(list)
    for x in rows("warmup"):
        groups[x["n"]].append((float(x["withWarmup"]), float(x["withoutWarmup"])))

    data = []
    for n, v in groups.items():
        a = sum(p for p, _ in v) / len(v)
        b = sum(q for _, q in v) / len(v)
        data.append([n, round(a), round(b), f"{abs(a - b) / a * 100:.3f}"])

    save("warmup", ["n", "p_all", "p_without_30_days", "diff_percent"], data)


# таблиця 3.6
def analytic():
    v = {r["case"]: r for r in rows("verification_stoch") if r["case"] != "V4"}
    data = []

    for case, gmax in (("V11", 624), ("V12", 1344)):
        r = v[case]
        a = int(r["arrived"])
        immediate = (int(r["nI"]) + int(r["cI"])) / a * 100
        expired = int(r["expired"]) / a * 100

        data.append([case, "негайні виходи, %", f"{96 / 1440 * 100:.2f}", f"{immediate:.2f}"])
        data.append([case, "застарілі, %", f"{(gmax - 240) / 1440 * 100:.2f}", f"{expired:.2f}"])

    r = v["V1"]
    a = int(r["arrived"])
    minutes = 5 * 365 * 1440
    little = float(r["meanWaiting"]) / (a / minutes)
    categories = (int(r["placed"]) * float(r["meanWaitPlaced"]) + int(r["expired"]) * 240) / a

    data.append(["V1", "надходження, шт.", round(minutes / 20), a])
    data.append(["V1", "час пауз, хв", 96 * 365 * 5, round(float(r["pauseTime"]))])
    data.append(["V1", "очікування: Літтл / за категоріями, хв", f"{little:.1f}", f"{categories:.1f}"])

    r = v["V13"]
    data.append(["V13", "час реклами за насичення, хв", 96 * 365 * 5, round(float(r["airtime"]))])
    data.append(["V13", "кількість очікуваних за Літтлом, шт.", "240.0", f"{float(r['meanWaiting']):.1f}"])

    save("analytic", ["case", "value", "theory", "model"], data)


# таблиця 4.5
def demand():
    groups = collections.defaultdict(list)
    for r in rows("demand"):
        groups[r["meanInterval"]].append({k: float(x) for k, x in r.items()})

    data = []
    for m, v in groups.items():
        b = max(v, key=lambda x: x["meanP"])
        ratio = 72 * 20 / float(m) * 3 / 96
        t = f"{b['meanT']:.3f}" if b["meanP"] > 0 else "не окупається"

        data.append([m, f"{ratio:.2f}", int(b["n"]), f"{b['meanP'] / 1e6:.2f}", t,
                     f"{b['expired'] * 100:.1f}", f"{b['free'] * 100:.1f}"])

    save("demand", ["interval", "demand_to_pause_time", "best_n", "p_mln", "T", "expired_percent", "free_percent"], data)


# таблиця 5.1
def proposals():
    all_rows = rows("proposals")
    base = next(float(r["meanT"]) for r in all_rows if r["name"] == "base")

    data = []
    for r in all_rows:
        t = float(r["meanT"])
        data.append([r["name"], r["bestN"], f"{float(r['meanP']) / 1e6:.3f}", f"{t:.4f}", f"{(t / base - 1) * 100:+.2f}"])

    save("proposals", ["name", "best_n", "p_mln", "T", "change_percent"], data)


# неповна бета-функція, неперервний дріб (Numerical Recipes)
def _betacf(a, b, x):
    tiny = 1e-300
    c = 1.0
    d = 1 - (a + b) * x / (a + 1)
    d = 1 / (d if abs(d) > tiny else tiny)
    h = d

    for m in range(1, 300):
        for aa in (m * (b - m) * x / ((a - 1 + 2 * m) * (a + 2 * m)),
                   -(a + m) * (a + b + m) * x / ((a + 2 * m) * (a + 1 + 2 * m))):
            d = 1 + aa * d
            d = 1 / (d if abs(d) > tiny else tiny)
            c = 1 + aa / c
            c = c if abs(c) > tiny else tiny
            h *= d * c

        if abs(d * c - 1) < 3e-14:
            break

    return h


def f_cdf(f, d1, d2):
    a, b = d1 / 2, d2 / 2
    x = d1 * f / (d1 * f + d2)
    if x <= 0:
        return 0.0

    bt = math.exp(math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b) + a * math.log(x) + b * math.log(1 - x))
    if x < (a + 1) / (a + b + 2):
        return bt * _betacf(a, b, x) / a
    return 1 - bt * _betacf(b, a, 1 - x) / b


def f_crit(d1, d2, alpha=0.05):
    lo, hi = 0.0, 100.0
    for _ in range(200):
        mid = (lo + hi) / 2
        if f_cdf(mid, d1, d2) < 1 - alpha:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


# дисперсійний аналіз, таблиця 4.4
def anova():
    r = 10
    data = []

    for first in (1, 3):
        sel = [x for x in rows("search") if first <= int(x["n"]) <= 32]
        k = len(sel)
        means = [float(x["meanT"]) for x in sel]
        grand = sum(means) / k

        ss_a = r * sum((m - grand) ** 2 for m in means)
        ss_e = (r - 1) * sum(float(x["stdT"]) ** 2 for x in sel)
        df_a, df_e = k - 1, k * (r - 1)
        f = (ss_a / df_a) / (ss_e / df_e)

        data.append([f"{first}-32",
                     f"{ss_a:.4e}", df_a, f"{ss_a / df_a:.4e}",
                     f"{ss_e:.4e}", df_e, f"{ss_e / df_e:.4e}",
                     round(f), f"{f_crit(df_a, df_e):.2f}"])

    save("anova", ["n_range", "SS_between", "df_between", "MS_between",
                   "SS_within", "df_within", "MS_within", "F", "F_crit"], data)


if __name__ == "__main__":
    replications()
    transient()
    warmup()
    analytic()
    demand()
    proposals()
    anova()
