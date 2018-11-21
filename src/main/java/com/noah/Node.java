package com.noah;

import java.util.HashSet;
import java.util.Set;

public class Node {
    private Integer id;//节点编号
    private Integer parentId;//父节点编号
    private Integer rootId;//根节点编号
    private Integer activeSlot;//活跃时隙
    private Integer level;//节点所在层数
    private Integer covNodeId;//覆盖当前节点的节点编号
    private Set<Integer> transSet;//传输时隙集合
    private Set<Node> coveringSet;//覆盖节点集合

    public Node(Integer id, Integer activeSlot) {
        this.id = id;
        this.activeSlot = activeSlot;
        parentId = -1;
        rootId = -1;
        covNodeId = -1;
        transSet = new HashSet<>();
        coveringSet = new HashSet<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getRootId() {
        return rootId;
    }

    public void setRootId(Integer rootId) {
        this.rootId = rootId;
    }

    public Integer getActiveSlot() {
        return activeSlot;
    }

    public void setActiveSlot(Integer activeSlot) {
        this.activeSlot = activeSlot;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getCovNodeId() {
        return covNodeId;
    }

    public void setCovNodeId(Integer covNodeId) {
        this.covNodeId = covNodeId;
    }

    public Set<Integer> getTransSet() {
        return transSet;
    }

    public void setTransSet(Set<Integer> transSet) {
        this.transSet = transSet;
    }

    public Set<Node> getCoveringSet() {
        return coveringSet;
    }

    public void setCoveringSet(Set<Node> coveringSet) {
        this.coveringSet = coveringSet;
    }

}
