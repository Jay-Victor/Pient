package com.pient.app.data.i18n

/** canvas 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface CanvasStrings {
    val noBranchTree: String
    val viewNodeDetails: String
    val createBranch: String
    val switchBranch: String
    val nodeDetails: String
    val noAnswer: String
}

object ZhCanvas : CanvasStrings {
    override val noBranchTree: String = "当前会话暂无分支树"
    override val viewNodeDetails: String = "查看节点详情"
    override val createBranch: String = "创建分支"
    override val switchBranch: String = "切换分支"
    override val nodeDetails: String = "节点详情"
    override val noAnswer: String = "（暂无回答）"
}

object EnCanvas : CanvasStrings {
    override val noBranchTree: String = "No branch tree in this session yet"
    override val viewNodeDetails: String = "View node details"
    override val createBranch: String = "Create branch"
    override val switchBranch: String = "Switch branch"
    override val nodeDetails: String = "Node details"
    override val noAnswer: String = "(No answer yet)"
}
