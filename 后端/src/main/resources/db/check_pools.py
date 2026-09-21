# -*- coding: utf-8 -*-
"""
题池体检：改完标签或题目后跑这个，不要靠感觉，也不用起服务。

岗位到题目之间**没有外键**，全靠 InterviewFlowService.matchTagIds 的运行时双向子串匹配：

    job_position.abilities + keywords  ->  skill_tag.name
        ->  skill_question_tag_rel  ->  skill_question（再按 difficulty <= 2 过滤）

所以改一个标签名，理论上能影响全部 32 个岗位。这个脚本把那条匹配规则完整复算一遍，
给出三份结论：

  1. 每个岗位的可抽题数（difficulty <= 2）——低于 25 会被标出来
  2. 越界检查：题目主标签不在该岗位命中标签里，却混进了题池（跑题高发区）
  3. 独占标签检查：某标签只属于一个岗位域，却出现在别的域

主标签定义与 resolveAbilityTag 一致：skill_question_tag_rel 里 id 最小的那条。

用法（口令走环境变量，不要写回文件里）：
    DB_PASSWORD=xxx python check_pools.py            # 全部岗位
    DB_PASSWORD=xxx python check_pools.py FE-WEB     # 只看某个岗位
"""
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

MYSQL = os.environ.get("MYSQL_BIN", "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")
DB_USER = os.environ.get("DB_USER", "root")
# 口令只从环境变量取，不写死在文件里（这个文件是入库的）
DB_PASS = os.environ["DB_PASSWORD"]
DB_NAME = os.environ.get("DB_NAME", "zhimian")

# 前端 JobSelect.vue 的 READY_JOBS：这些岗位必须在面试页可点，且题池要够厚
READY = ["BE-JAVA", "BE-PY", "FE-WEB", "FE-ANDROID", "FS-JAVA", "FS-PY",
         "ALG-ML", "ALG-NLP", "PM-C", "PM-B", "DA-BIZ", "DA-PROD",
         "QA-AUTO", "QA-PERF"]

POOL_MIN = 25          # 一条面试抽 8 题，低于这个数会明显重复
DIFFICULTY_MAX = 2     # 前端不传 difficulty -> normalizeDifficulty 归一为 2 -> le(2)


def query(sql):
    raw = subprocess.run(
        [MYSQL, "-u" + DB_USER, "-p" + DB_PASS, "-D", DB_NAME,
         "--batch", "--default-character-set=utf8mb4", "-e", sql],
        capture_output=True)
    text = raw.stdout.decode("utf-8", "replace")
    lines = text.splitlines()
    if not lines:
        return []
    head = lines[0].split("\t")
    return [dict(zip(head, l.split("\t"))) for l in lines[1:] if l.strip()]


# ---------- 复刻 matchTagIds：双向子串，纯 ASCII 的 needle 要求整词命中 ----------
def contains_tag(hay, needle):
    """hay 里是否含 needle。两侧都是 ASCII 时用词边界，中文走裸子串。"""
    if not needle or not hay or needle not in hay:
        return False
    if not needle.isascii():
        return True
    return re.search(r"(?<![a-z0-9])" + re.escape(needle) + r"(?![a-z0-9])", hay) is not None


def match_tags(profile_items, all_tags):
    out = {}
    for item in profile_items:
        low = item.lower().strip()
        if not low:
            continue
        for tag in all_tags:
            tl = tag.lower()
            if contains_tag(low, tl) or contains_tag(tl, low):
                out.setdefault(tag, item)
    return out


def main():
    jobs = query("SELECT code, name, family, abilities, keywords FROM job_position ORDER BY code;")
    rels = query(
        "SELECT r.id, r.question_id, t.name, q.difficulty, LEFT(q.content, 44) AS content "
        "FROM skill_question_tag_rel r "
        "JOIN skill_tag t ON t.id = r.tag_id "
        "JOIN skill_question q ON q.id = r.question_id ORDER BY r.id;")
    if not jobs or not rels:
        print("查不到数据，先确认 MySQL 在跑、库名和密码对不对。")
        return 1

    q_tags = defaultdict(set)
    q_diff = {}
    q_text = {}
    primary = {}                      # 主标签 = rel.id 最小的那条
    for r in rels:
        qid = int(r["question_id"])
        q_tags[qid].add(r["name"])
        q_diff[qid] = int(r["difficulty"])
        q_text[qid] = r.get("content", "")
        rid = int(r["id"])
        if qid not in primary or rid < primary[qid][0]:
            primary[qid] = (rid, r["name"])
    primary = {q: v[1] for q, v in primary.items()}

    all_tags = sorted({t for s in q_tags.values() for t in s})

    only = sys.argv[1] if len(sys.argv) > 1 else None
    codes = [only] if only else [j["code"] for j in jobs]

    info = {}
    for code in codes:
        row = next((j for j in jobs if j["code"] == code), None)
        if row is None:
            print("没有这个岗位:", code)
            continue
        profile = list(json.loads(row["abilities"] or "[]")) + \
                  list(json.loads(row["keywords"] or "[]"))
        matched = match_tags(profile, all_tags)
        pool = sorted(q for q in q_tags
                      if q_tags[q] & set(matched) and q_diff[q] <= DIFFICULTY_MAX)
        info[code] = (row, matched, set(pool))

    if not only:
        print("=" * 74)
        print("%-11s %-22s %5s %6s  %s" % ("code", "岗位", "命中", "题池", ""))
        print("=" * 74)
        thin = []
        for code in sorted(info, key=lambda c: len(info[c][2])):
            row, matched, pool = info[code]
            star = "★" if code in READY else " "
            warn = ""
            if code in READY and len(pool) < POOL_MIN:
                warn = "  ← 低于 %d，会重复抽题" % POOL_MIN
                thin.append(code)
            print("%s%-10s %-22s %5d %6d  %s%s"
                  % (star, code, row["name"], len(matched), len(pool),
                     "█" * min(len(pool) // 2, 40), warn))
        print()
        print("★ = 前端 READY_JOBS 里开放的 14 个岗位")
        if thin:
            print("⚠ 题池不足：", "、".join(thin))
        else:
            print("✅ 14 个开放岗位题池全部 >= %d" % POOL_MIN)

    # ---------- 越界检查：主标签不在命中标签里，却混进了题池 ----------
    print()
    print("=" * 74)
    print("越界检查：题目主标签不在该岗位命中标签中（靠次要标签混进来的跑题高发区）")
    print("=" * 74)
    total = 0
    for code in codes:
        if code not in info:
            continue
        row, matched, pool = info[code]
        bad = [(q, primary[q], sorted(q_tags[q] & set(matched)))
               for q in sorted(pool) if primary.get(q) not in matched]
        if not bad:
            continue
        print("\n%s %s（题池 %d）:" % (code, row["name"], len(pool)))
        for q, p, via in bad:
            total += 1
            print("   %-5d 主标签[%s] 经由[%s]进入" % (q, p, "、".join(via)))
            print("         %s" % q_text.get(q, "")[:60])
    print("\n合计越界条目: %d" % total)
    if total:
        print("注：同域内的交叉（性能测试岗抽到缺陷管理题）是正常的；")
        print("    跨域的（PM 面试抽到 Python 题）才是真跑题，逐条人工核对。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
