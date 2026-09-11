"""重建 Pient 内置价格表：核实现有行 + 补充缺失型号。
数据源：
  * models.dev/api.json（pi 官方生态同一数据源，USD/百万 tokens）—— 2026-09-11 抓取
  * 官方人民币价：DeepSeek api-docs（空闲/高峰取空闲）、智谱 bigmodel.cn/pricing、
    Kimi K3（公开报道 ¥20/¥100）、小米 mimo.mi.com/docs/price、阿里百炼 qwen3.8-flash / qwen3.8-max / qwen3.7-max
输出：app/src/main/assets/model_pricing.tsv（provider|model|mode|input|output|cachedOrCount|currency[|cacheWrite]）
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TSV = os.path.join(ROOT, 'app/src/main/assets/model_pricing.tsv')
CACHE = os.path.join(os.environ.get('LOCALAPPDATA', '/tmp'), 'Temp/models_dev.json')

# 用法：
#   curl -sS -o "$LOCALAPPDATA/Temp/models_dev.json" https://models.dev/api.json
#   python scripts/build_model_pricing.py
# 表会保留未被覆盖的旧行（Operit 基线），同键行以 models.dev / 官方人民币价为准。
if not os.path.exists(CACHE):
    raise SystemExit('缺少 models.dev 缓存：先 curl -sS -o "%s" https://models.dev/api.json' % CACHE)
DEV = json.load(open(CACHE, encoding='utf-8'))

# ── 1) 官方人民币行（已核实，键 = (provider, model)）────────────────────────────
CNY_ROWS = {
    # DeepSeek 官方（api-docs 定价页；高峰/空闲两档，这里取「空闲时段」，见文件头注释）
    ('deepseek', 'deepseek-v4-flash'):            (1,   4,    0.02,  None),
    ('deepseek', 'deepseek-v4-flash-vision-exp'): (1,   4,    0.02,  None),
    ('deepseek', 'deepseek-v4-pro'):              (4.5, 13.5, 0.15,  None),
    ('deepseek', 'deepseek-chat'):                (1,   4,    0.02,  None),  # V4-Flash 非思考模式（旧名逐步弃用）
    ('deepseek', 'deepseek-reasoner'):            (1,   4,    0.02,  None),  # V4-Flash 思考模式
    ('deepseek', 'deepseek-flash'):               (1,   4,    0.02,  None),
    # 智谱（bigmodel.cn/pricing）
    ('zai-coding-cn', 'glm-5.3'):        (8,   28,  2,     None),
    ('zai-coding-cn', 'glm-5.3-flash'):  (0.4, 1.4, 0.115, None),   # 限时 5 折（刊例 0.8/2.8）
    ('zai-coding-cn', 'glm-5.2'):        (8,   28,  2,     None),
    ('zai-coding-cn', 'glm-5.1'):        (6,   24,  1.3,   None),
    ('zai-coding-cn', 'glm-5v-turbo'):   (5,   22,  1.2,   None),
    ('zai-coding-cn', 'glm-4.7'):        (3,   14,  0.6,   None),
    ('zai-coding-cn', 'glm-4.5-air'):    (0.8, 2,   0.16,  None),
    # Kimi（公开报道，CN 平台）
    ('moonshotai-cn', 'kimi-k3'): (20, 100, 0,      None),
    # 小米 MiMo（mimo.mi.com/docs/price）
    ('xiaomi', 'mimo-v2.5'):     (1, 2, 0.02,  None),
    ('xiaomi', 'mimo-v2.5-pro'): (3, 6, 0.025, None),
    # 阿里百炼（help.aliyun.com/model-studio）
    ('qwen-token-plan-cn', 'qwen3.8-flash'): (0.8, 2.7, 0.1, 1.25),
    ('qwen-token-plan-cn', 'qwen3.8-max'):   (12,  36,  0,   None),
    ('qwen-token-plan-cn', 'qwen3.7-max'):   (12,  36,  0,   None),
}

# ── 2) models.dev → Pient 服务商 id 映射（USD/百万 tokens）────────────────────
FAMILY_MAP = {
    'deepseek':   ['deepseek'],
    'zhipuai':    ['zai'],
    'moonshotai': ['moonshotai'],
    'xiaomi':     ['xiaomi'],
    'alibaba':    ['*'],          # 通义千问系：按模型名回退命中（任何服务商）
    'google':     ['google'],
    'anthropic':  ['anthropic'],
    'openai':     ['openai'],
    'xai':        ['xai'],
}
# 非对话计费模型（图像/视频/语音/向量/检索/实时类）不进价格表
SKIP_KEYWORDS = ('embedding', 'tts', 'asr', 'image', 'video', 'veo', 'lyria', 'realtime',
                 'live', 'computer-use', 'deep-research', 'audio', 'videointelligence',
                 'audiointelligence', 'gpt-image', 'chatgpt-image', 'grok-imagine', 'grok-build')

def num(v):
    return None if v is None else float(v)


def fmt(v):
    if v is None:
        return '0'
    return str(int(v)) if float(v) == int(float(v)) else ('%g' % float(v))

rows = {}   # (provider, model) -> (mode, i, o, c, cur, cw)

# 2.1 现有表（Operit 基线）先读入
with open(TSV, encoding='utf-8') as f:
    old_lines = [l.rstrip('\n') for l in f if l.strip() and not l.startswith('#')]
for line in old_lines:
    c = line.split('|')
    if len(c) < 7:
        continue
    prov, model = c[0], c[1]
    rows[(prov, model)] = (c[2], c[3], c[4], c[5], c[6], c[7] if len(c) > 7 else None)
old_count = len(rows)

# 2.2 models.dev 行覆盖同键（权威源优先）
added, replaced = 0, 0
for family, targets in FAMILY_MAP.items():
    models = DEV.get(family, {}).get('models', {})
    for mid, meta in models.items():
        if any(k in mid.lower() for k in SKIP_KEYWORDS):
            continue
        cost = meta.get('cost') or {}
        i, o = num(cost.get('input')), num(cost.get('output'))
        if i is None or o is None or (i == 0 and o == 0):
            continue
        cr, cw = num(cost.get('cache_read')), num(cost.get('cache_write'))
        if family == 'deepseek':
            pass
        payload = ('TOKEN', fmt(i), fmt(o), fmt(cr if cr is not None else 0), 'USD', fmt(cw) if cw else None)
        for prov in targets:
            key = (prov, mid)
            if key in rows:
                if rows[key] != payload:
                    replaced += 1
            else:
                added += 1
            rows[key] = payload

# 2.3 官方人民币行（最高优先，覆盖 USD 行）
cny_added = 0
for (prov, model), (i, o, cr, cw) in CNY_ROWS.items():
    key = (prov, model)
    if key not in rows:
        cny_added += 1
    rows[key] = ('TOKEN', fmt(i), fmt(o), fmt(cr), 'CNY', fmt(cw) if cw else None)

def fmt(v):
    if v is None:
        return '0'
    return str(int(v)) if float(v) == int(float(v)) else ('%g' % float(v))

# 重新写（含表头注释）；排序：按 provider、model
lines = [
    '# Pient 内置模型价格表  provider|model|mode(TOKEN/COUNT)|input|output|cachedInput(or pricePerRequest)|currency[|cacheWrite]',
    '# 单位：每百万 tokens；CNY 行为官方人民币价，USD 行为 models.dev（pi 生态同源）美元价，',
    '# 应用按「模型币种 + 汇率设置」折算人民币展示；服务商配置里的定价（弹窗编辑）优先级高于本表。',
    '# 来源与抓取时间：models.dev 2026-09-11；DeepSeek api-docs（高峰/空闲两档，表内取空闲时段）；',
    '#   智谱 bigmodel.cn/pricing；小米 mimo.mi.com/docs/price；阿里百炼 model-studio；Kimi K3 公开定价。',
]
for (prov, model) in sorted(rows.keys()):
    mode, i, o, c, cur, cw = rows[(prov, model)]
    line = '|'.join([prov, model, mode, i, o, c, cur] + ([cw] if cw else []))
    lines.append(line)

open(TSV, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print(f'旧表 {old_count} 行 → 新表 {len(rows)} 行（models.dev 新增 {added}、覆盖 {replaced}、官方人民币新增 {cny_added}）')
