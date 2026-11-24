import collections
import csv
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from matplotlib.ticker import FuncFormatter

REPORT = Path(__file__).resolve().parent.parent
RES = REPORT / "results"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else REPORT / "fig"
OUT.mkdir(parents=True, exist_ok=True)

plt.rcParams.update({
    "font.family": "Times New Roman",
    "font.size": 12,
    "mathtext.fontset": "stix",
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "grid.color": "#e4e3df",
    "grid.linewidth": .7,
    "axes.axisbelow": True,
    "lines.linewidth": 1.6,
    "axes.edgecolor": "#52514e",
    "axes.formatter.use_locale": False,
})

C = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"]
MUTED = "#52514e"
N_LABEL = "Кількість рекламних пауз n, шт."


def dec(digits):
    return FuncFormatter(lambda v, _: f"{v:.{digits}f}".replace(".", ","))


def rows(name):
    with open(RES / f"{name}.csv", encoding="utf8") as f:
        return list(csv.DictReader(f))


def save(fig, name):
    fig.savefig(OUT / f"{name}.png", dpi=220, bbox_inches="tight", pad_inches=.05)
    plt.close(fig)


# рисунок 1.1
def timeline():
    with plt.rc_context({"axes.grid": False, "axes.spines.left": False}):
        fig, ax = plt.subplots(figsize=(9.6, 2.5))
        ax.set_xlim(0, 24)
        ax.set_ylim(0, 1)

        ax.add_patch(Rectangle((0, .28), 16, .42, fc="#d6e6f8", ec="none"))
        ax.add_patch(Rectangle((16, .28), 8, .42, fc="#e4e3df", ec="none"))

        seg, L = 160 / 60, 16 / 60
        for k in range(6):
            ax.add_patch(Rectangle(((k + 1) * seg - L, .28), L, .42, fc="#eb6834", ec="none"))

        ax.text(1.3, .49, "програма", ha="center", va="center", fontsize=11)
        ax.text(20, .49, "мовлення немає (8 год)", ha="center", va="center", fontsize=11)
        ax.annotate("пауза L = 16 хв", xy=(seg - L / 2, .70), xytext=(seg - L / 2, .9), ha="center", fontsize=11,
                    arrowprops=dict(arrowstyle="-", lw=.8, color=MUTED))
        ax.annotate("", xy=(seg, .14), xytext=(2 * seg, .14), arrowprops=dict(arrowstyle="<->", lw=.8, color=MUTED))
        ax.text(1.5 * seg, .0, "сегмент 160 хв", ha="center", fontsize=11)

        ax.set_yticks([])
        ax.set_xticks(range(0, 25, 2))
        ax.set_xlabel("Час від початку доби, год")
        save(fig, "f1_2_timeline")


# рисунки 4.1 і 4.2
def transient():
    tr = rows("transient")

    for n, name in (("5", "f4_1_transient5"), ("2", "f4_2_transient2")):
        fig, ax = plt.subplots(figsize=(8.4, 3.6))

        for s in range(5):
            d = [(float(x["days"]), float(x["profitPerYear"]) / 1e6)
                 for x in tr if x["n"] == n and x["seed"] == str(s)]
            ax.plot(*zip(*d), color=C[s], marker="o", ms=3, label=f"прогін {s + 1}")

        ax.set_xscale("log")
        ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:g}"))
        ax.yaxis.set_major_formatter(dec(3 if n == "5" else 2))
        ax.set_xlabel("Модельний час, діб (логарифмічна шкала)")
        ax.set_ylabel("Накопичене p(t), млн грн/рік")
        ax.legend(frameon=False, ncol=5, loc="upper center", bbox_to_anchor=(.5, 1.15), fontsize=11,
                  handlelength=1.4, columnspacing=1)
        save(fig, name)


# рисунки 4.3, 4.4 і 4.5
def search():
    se = [r for r in rows("search") if int(r["n"]) <= 32]
    xs = [int(r["n"]) for r in se]
    T = [float(r["meanT"]) for r in se]
    H = [float(r["halfT"]) for r in se]

    fig, ax = plt.subplots(figsize=(8.4, 3.8))
    ax.plot(xs, T, color=C[0], marker="o", ms=4)
    ax.set_yticks([i / 5 for i in range(10)])
    ax.yaxis.set_major_formatter(dec(1))
    ax.set_ylim(0, 1.85)
    ax.set_xlabel(N_LABEL)
    ax.set_ylabel("Період окупності T, років")
    ax.set_xticks(range(0, 33, 4))

    best = min(range(len(T)), key=T.__getitem__)
    label = f"мінімум: n = {xs[best]}, T = {T[best]:.4f} року".replace(".", ",")
    ax.annotate(label, xy=(xs[best], T[best]), xytext=(9, .6), arrowprops=dict(arrowstyle="-", lw=.8, color=MUTED))
    save(fig, "f4_3_search")

    fig, ax = plt.subplots(figsize=(8.4, 3.8))
    sel = [i for i, x in enumerate(xs) if x >= 3]
    ax.errorbar([xs[i] for i in sel], [T[i] for i in sel], yerr=[H[i] for i in sel],
                color=C[0], marker="o", ms=4, capsize=2.5)
    ax.set_xlabel(N_LABEL)
    ax.set_ylabel("Період окупності T, років")
    ax.yaxis.set_major_formatter(dec(3))
    ax.set_xticks(range(3, 33, 2))

    lo, hi = min(T[i] for i in sel), max(T[i] for i in sel)
    pad = (hi - lo) * .12
    for n in (5, 10, 14, 26):
        ax.text(n, T[n - 1] - pad * .9, f"n = {n}", ha="center", fontsize=11)
    ax.set_ylim(lo - pad * 1.6, hi + pad)
    save(fig, "f4_4_zoom")

    fig, ax = plt.subplots(figsize=(8.4, 3.9))
    lines = (("expired", "застарілі повідомлення", C[0], "-"),
             ("cut", "обрізані (від розміщених)", C[1], "--"),
             ("free", "вільний час пауз", C[2], ":"))
    for key, lab, c, ls in lines:
        ax.plot(xs, [float(r[key]) * 100 for r in se], color=c, ls=ls, marker="o", ms=3, label=lab)

    ax.set_xlabel(N_LABEL)
    ax.set_ylabel("Частка, %")
    ax.set_xticks(range(0, 33, 4))
    ax.legend(frameon=False, ncol=3, loc="upper center", bbox_to_anchor=(.5, 1.13), fontsize=11)
    save(fig, "f4_5_shares")


# рисунок 4.6
def demand():
    d = collections.defaultdict(list)
    for r in rows("demand"):
        d[r["meanInterval"]].append((int(r["n"]), float(r["meanP"]) / 1e6))

    fig, ax = plt.subplots(figsize=(8.4, 4))
    for m, c in zip(("10", "20", "30", "40", "60", "80"), C):
        v = d[m]
        ax.plot(*zip(*v), color=c, label=f"{m} хв")
        b = max(v, key=lambda a: a[1])
        ax.plot(*b, marker="o", ms=7, color=c, mec="white")

    ax.axhline(0, color=MUTED, lw=.8)
    ax.set_xlabel(N_LABEL)
    ax.set_ylabel("Прибуток p, млн грн/рік")
    ax.set_xticks(range(0, 33, 4))
    ax.legend(frameon=False, ncol=6, title="Середній інтервал надходження (точка – максимум кривої)",
              loc="upper center", bbox_to_anchor=(.5, 1.22), fontsize=11, title_fontsize=11,
              columnspacing=1, handlelength=1.4)
    save(fig, "f4_6_demand")


# рисунок 5.1
def proposal_curve():
    pc = rows("proposals_curve")
    fig, ax = plt.subplots(figsize=(8.4, 3.8))

    for name, lab, c, ls in (("base", "поточне правило FIFO", C[0], "-"),
                             ("fit", "правило «найбільше, що вміщується»", C[1], "--")):
        v = [(int(r["n"]), float(r["meanT"])) for r in pc if r["name"] == name and int(r["n"]) >= 3]
        ax.plot(*zip(*v), color=c, ls=ls, marker="o", ms=3.5, label=lab)

    ax.set_xlabel(N_LABEL)
    ax.set_ylabel("Період окупності T, років")
    ax.yaxis.set_major_formatter(dec(3))
    ax.set_xticks(range(3, 33, 2))
    ax.legend(frameon=False, ncol=2, loc="upper center", bbox_to_anchor=(.5, 1.13), fontsize=11)
    save(fig, "f5_1_fit")


if __name__ == "__main__":
    timeline()
    transient()
    search()
    demand()
    proposal_curve()
    print("Рисунки збережено в", OUT)
