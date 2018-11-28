package com.noah;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class Graph {
    private Integer nodeCount;//网络节点数
    private Integer slotCount;//单周期时隙数
    private Integer maxLevel;//图的最大层数
    private Node[] nodeList;//节点集合，编号为0的节点表示Source，其活跃时隙取-1
    private List<Set<Integer>> adjTable;//描述图拓扑结构的邻接表，adjTable(i)表示编号为i的邻接点编号集合
    private Set<Integer> backbone;//广播骨架节点编号集合

    private Graph() {
        backbone = new HashSet<>();
        maxLevel = 0;
        //manuallyInit();
        autoInit(20,5,0);
        //transformTopology();
    }

    /**
     * 利用BFS计算各节点的层
     */
    private void calNodeLevel() {
        int peek;
        boolean[] visited = new boolean[nodeCount];
        Queue<Integer> queue = new LinkedList<>();

        nodeList[0].setLevel(0);//源点的层为0
        visited[0] = true;
        queue.offer(0);
        while (!queue.isEmpty()) {
            peek = queue.poll();
            for (Integer i : adjTable.get(peek))
                if (!visited[i]) {
                    visited[i] = true;
                    nodeList[i].setParentId(peek);//为每个节点设置初始的父节点
                    nodeList[i].setLevel(nodeList[peek].getLevel() + 1);
                    if (nodeList[i].getLevel() > maxLevel)//计算出图的最大层数
                        maxLevel = nodeList[i].getLevel();
                    queue.offer(i);
                }
        }
    }

    /**
     * 获得活跃时隙为timeSlot的节点集合
     * @param timeSlot 查询的时隙
     * @return 对应的节点集合
     */
    private Set<Node> getActiveSlotNodeSet(int timeSlot) {
        Set<Node> nodeSet = new HashSet<>();
        for (Node n : nodeList)
            if (n.getActiveSlot().equals(timeSlot))
                nodeSet.add(n);
        return nodeSet;
    }

    /**
     * 获得节点在指定时隙的邻节点集合
     * @param id 所选节点的编号
     * @param timeSlot 查询的时隙
     * @return 对应的节点集合
     */
    private Set<Node> getNeighborsAtSlot(int id, int timeSlot, List<Set<Integer>> tempAdjTable) {
        Set<Node> nodeSet = getActiveSlotNodeSet(timeSlot);
        return nodeSet.stream().filter(node -> tempAdjTable.get(id).contains(node.getId())).collect(Collectors.toSet());
    }

    /**
     * 获得时隙为timeSlot的覆盖节点集合
     * @param timeSlot 查询的时隙
     * @return 对应的节点集合
     */
    private Set<Node> getCoveringSlotNodeSet(int timeSlot) {
        //拷贝邻接表，防止修改邻接关系时影响全局的邻接表
        List<Set<Integer>> tempAdjTable = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++)
            tempAdjTable.add(new HashSet<>(adjTable.get(i)));

        Set<Node> Ci = new HashSet<>();
        Set<Integer> S = new HashSet<>();
        Set<Node> P = getActiveSlotNodeSet(timeSlot);
        for (int i = 0; i < nodeCount; i++)//源点不会被覆盖，但可以覆盖其他节点
            S.add(i);

        int selectedId, maxArg;
        Set<Node> Niv;
        while (!P.isEmpty()) {
            selectedId = -1;
            maxArg = 0;
            for (Integer i : S)
                //若selectedId未初始化，或新节点在当前时隙的邻节点数大于原节点，
                //或新节点在当前时隙的邻节点数等于原节点但编号比原节点小，更新selectedId
                if (selectedId == -1 || getNeighborsAtSlot(i, timeSlot, tempAdjTable).size() > maxArg || getNeighborsAtSlot(i, timeSlot, tempAdjTable).size() == maxArg && i < selectedId) {
                    selectedId = i;
                    maxArg = getNeighborsAtSlot(selectedId, timeSlot, tempAdjTable).size();
                }
            Niv = getNeighborsAtSlot(selectedId, timeSlot, tempAdjTable);
            Ci.add(nodeList[selectedId]);//将当前选中的节点加入集合
            S.remove(selectedId);//将本次选中的节点从节点集合中移除
            P.removeAll(Niv);//将本次选中节点的邻节点从当前时隙活跃节点集合中移除
            for (Set<Integer> set : tempAdjTable) //修改邻接表，将当前选中节点的覆盖节点从其他节点的邻接表中移除
                for (Node n : Niv)
                    set.remove(n.getId());
            for (Node n : Niv) {//设置被覆盖节点的CovNode和选中节点的覆盖集合
                n.setCovNodeId(selectedId);
                nodeList[selectedId].getCoveringSet().add(n);
            }
            nodeList[selectedId].getTransSet().add(timeSlot);//将当前时隙加入选中节点的传输时隙集合
        }
        return Ci;
    }

    /**
     * 获得指定层的覆盖节点集合，需要在计算完所有时隙的覆盖节点后才能调用
     * @param level 指定的层数
     * @return 对应的节点集合
     */
    private Set<Node> getLevelBasedSet(int level) {
        Set<Node> nodeSet = new HashSet<>();

        //用节点的覆盖集合是否非空判断其是否是覆盖节点，用节点所在层判断是否需要加入返回值集合
        for (int i = 0; i < nodeCount; i++)
            if (nodeList[i].getLevel() == level && !nodeList[i].getCoveringSet().isEmpty())
                nodeSet.add(nodeList[i]);
        return nodeSet;
    }

    /**
     * 将节点v加入广播主干
     * @param vId 表示待加入节点v
     * @param uId 可能存在的v的前驱
     */
    private void addToBackBone(int vId, int uId) {
        backbone.add(vId);
        if (uId != -1) {
            if (vId != uId) {
                nodeList[vId].setParentId(uId);
                nodeList[vId].setRootId(nodeList[uId].getRootId());
            }
        }
        else
            nodeList[vId].setRootId(vId);
    }

    /**
     * 自上而下遍历，将各覆盖节点加入广播主干，并建立数棵覆盖子树
     */
    private void constructSubTrees() {
        //初始化各节点的层
        calNodeLevel();

        //列表中第i个集合对应第i个时隙的覆盖节点集合
        //同时可以计算各节点的覆盖节点
        List<Set<Node>> coveringNodeSetList = new ArrayList<>();
        for (int i = 0; i < slotCount; i++)
            coveringNodeSetList.add(getCoveringSlotNodeSet(i));
        Node u;
        Set<Node> Sl;

        addToBackBone(0, -1);//将源点加入广播骨架

        //遍历所有层的覆盖节点集合
        for (int i = 1; i <= maxLevel; i++) {
            Sl = getLevelBasedSet(i);
            for (Node v :Sl)
                if (v.getRootId().intValue() != v.getId().intValue()) {
                    u = nodeList[v.getCovNodeId()];
                    if (u.getLevel().intValue() < v.getLevel().intValue())//Case 1.1
                        addToBackBone(v.getId(), u.getId());
                    else if (u.getLevel().intValue() == v.getLevel().intValue()) {
                        if (u.getRootId() == -1) {
                            if (v.getId().intValue() == u.getCovNodeId().intValue()) {//Case 1.2 & Case 1.3
                                //选择具有更多邻节点的那个作为新的根；若邻节点数相同，选择Id较小的
                                if (adjTable.get(v.getId()).size() > adjTable.get(u.getId()).size())
                                    u = v;
                                else if (adjTable.get(v.getId()).size() == adjTable.get(u.getId()).size())
                                    u = (u.getId() < v.getId()) ? u : v;
                            }
                            u.setRootId(u.getId());
                            addToBackBone(u.getId(), -1);
                        }
                        addToBackBone(v.getId(), u.getId());
                    }
                    else
                        addToBackBone(v.getId(), -1);
                    //remove
                }
        }
    }

    /**
     * 完成Stage1后，调用此方法获得所有的覆盖子树的根节点
     * @return 根节点列表，按所在层数由低到高排列
     */
    private List<Node> getRootNodes() {
        List<Node> nodes = new ArrayList<>();
        for (Integer i : backbone)
            if (nodeList[i].getRootId().equals(i) && i != 0)//不需要加入source
                nodes.add(nodeList[i]);
        Collections.sort(nodes, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                return o2.getLevel().compareTo(o1.getLevel());
            }
        });
        return nodes;
    }

    /**
     * 将节点x加入广播主干，并附加一个传输时隙t
     * @param xId 新加入的节点
     * @param pId 节点x的前驱
     * @param t 节点x新增的传输时隙
     */
    private void addToBackBone2(int xId, int pId, int t) {
        if (!backbone.contains(xId)) {
            backbone.add(xId);
            nodeList[xId].setParentId(pId);
            nodeList[xId].setRootId(nodeList[pId].getRootId());
        }
        nodeList[xId].getTransSet().add(t);
    }

    /**
     * 自下而上遍历所有覆盖子树根节点，并将各子树连接，完成广播主干
     */
    private void finalizeBackbone() {
        constructSubTrees();
        List<Node> rootNodes = getRootNodes();
        int covRoot, selectedC = -1;
        int P, grandP;
        Set<Integer> neighbors;

        for (Node v : rootNodes) {
            covRoot = nodeList[v.getCovNodeId()].getRootId();
            if (nodeList[covRoot].getLevel().intValue() < v.getLevel().intValue())//Case 2.1
                v.setParentId(v.getCovNodeId());
            else {
                neighbors = adjTable.get(v.getId());
                for (Integer c : neighbors) {
                    //Case 2.2 在v的邻节点中选择，其满足以下条件之一：
                    //1. 已经在主干中，且其根节点所在层比v低
                    //2. 覆盖它的节点所在子树的根节点所在层比v低
                    if (backbone.contains(c) && nodeList[nodeList[c].getRootId()].getLevel() < v.getLevel() ||
                            nodeList[nodeList[nodeList[c].getCovNodeId()].getRootId()].getLevel() < v.getLevel()) {
                        selectedC = c;
                        break;
                    }
                }
                if (selectedC != -1) {
                    v.setParentId(selectedC);
                    addToBackBone2(selectedC, nodeList[selectedC].getCovNodeId(), v.getActiveSlot());
                    selectedC = -1;
                }
                else {
                    P = v.getParentId();
                    grandP = nodeList[P].getParentId();
                    addToBackBone2(grandP, nodeList[grandP].getCovNodeId(), nodeList[P].getActiveSlot());
                    addToBackBone2(P, grandP, v.getActiveSlot());
                }
            }
            v.setRootId(nodeList[v.getParentId()].getRootId());
        }
    }

//=====================================模拟结果测试函数========================================
    /**
     * 手动输入拓扑
     */
    private void manuallyInit() {
        try {
            Scanner sc = new Scanner(new FileInputStream("./src/main/resources/test_data(new_1).txt"));

            //输入节点数和时隙数
            nodeCount = sc.nextInt();
            slotCount = sc.nextInt();

            //初始化节点列表和邻接表
            nodeList = new Node[nodeCount];
            adjTable = new ArrayList<>();
            for (int i = 0; i < nodeCount; i++)
                adjTable.add(new HashSet<>());

            //输入各节点的编号及活跃时隙
            int id, activeSlot;
            for (int i = 0; i < nodeCount; i++) {
                id = sc.nextInt();
                activeSlot = sc.nextInt();
                nodeList[id] = new Node(id, activeSlot);
            }

            //输入图的拓扑结构
            int s, e, eNum;
            eNum = sc.nextInt();//图的边数
            for (int i = 0; i < eNum; i++) {
                s = sc.nextInt();
                e = sc.nextInt();
                adjTable.get(s).add(e);
                adjTable.get(e).add(s);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 随机生成图的拓扑结构（基于LBAS的一跳模型）
     * @param nodeNum 需要生成的节点数
     * @param slotNum 当前图每个周期中的时隙数
     * @param additionalEdgeNum 额外生成的边数
     */
    private void autoInit(int nodeNum, int slotNum, int additionalEdgeNum) {
        Random random = new Random(System.currentTimeMillis());

        nodeCount = nodeNum;
        slotCount = slotNum;

        //初始化节点列表和邻接表
        nodeList = new Node[nodeCount];
        adjTable = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++)
            adjTable.add(new HashSet<>());

        //初始化节点，节点编号为 0 ~ (nodeNum-1)
        nodeList[0] = new Node(0, -1);//0号节点为Source
        for (int i = 1; i < nodeCount; i++)
            nodeList[i] = new Node(i, random.nextInt(slotCount));

        //为了保证图的连通性，首先随机创建一颗生成树
        Set<Integer> U = new HashSet<>();//U中保存了已加入生成树的节点
        Set<Integer> V = new HashSet<>();//V中保存了未加入生成树的节点
        U.add(0);//为U指定一个初始节点
        for (int i = 1; i < nodeCount; i++)
            V.add(i);

        int s, e;
        while (!V.isEmpty()) {
            s = (int) U.toArray()[random.nextInt(U.size())];//从U中随机取一个节点
            e = (int) V.toArray()[random.nextInt(V.size())];//从V中随机取一个节点

            V.remove(e);
            U.add(e);

            adjTable.get(s).add(e);
            adjTable.get(e).add(s);
        }

        //额外生成additionalEdgeNum条边
        for (int i = 0; i < additionalEdgeNum; i++) {
            do {
                s = random.nextInt(nodeCount);
                e = random.nextInt(nodeCount);
            } while (s == e || adjTable.get(s).contains(e));//当生成的起点和终点相同，或边已存在，则重新生成起点和终点
            adjTable.get(s).add(e);
            adjTable.get(e).add(s);
        }
    }

    /**
     * 原图计算完成后，调用变换拓扑函数前，需要调用该函数初始化各节点的所有数据结构
     */
    private void initState() {
        maxLevel = 0;
        backbone.clear();
        for (int i = 0; i < nodeCount; i++) {
            nodeList[i].setParentId(-1);
            nodeList[i].setRootId(-1);
            nodeList[i].setCovNodeId(-1);
            nodeList[i].getTransSet().clear();
            nodeList[i].getCoveringSet().clear();
        }
    }

    /**
     * 变换图的拓扑结构，将每个节点和其两跳节点相连
     */
    private void transformTopology() {
        //初始化各节点的所有数据结构
        initState();

        List<Set<Integer>> tempAdjTable = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++)
            tempAdjTable.add(new HashSet<>(adjTable.get(i)));
        Set<Integer> curNeighbors;

        for (int i = 0; i < nodeCount; i++) {
            curNeighbors = adjTable.get(i);
            for (Integer j : curNeighbors)
                tempAdjTable.get(i).addAll(adjTable.get(j));
            tempAdjTable.get(i).remove(i);
        }

        adjTable = tempAdjTable;
    }


    /**
     * 计算广播所用的传输次数之和，在调用finalizeBackbone后才能使用
     * @return 传输次数之和
     */
    private int calTotalTrans() {
        int cnt = 0;
        for (Integer i : backbone)
            cnt += nodeList[i].getTransSet().size();
        return cnt;
    }

    /**
     * 求广播主干中给定节点的子节点集合
     * @param id 给定的节点id
     * @return 子节点id集合
     */
    private Set<Integer> getChildSet(int id) {
        Set<Integer> childSet = new HashSet<>();
        for (Integer i : backbone)
            if (nodeList[i].getParentId() == id)
                childSet.add(i);
        return childSet;
    }

    /**
     * 计算广播所用的延迟，即从开始广播到消息传送到网络中每个节点所用的总时隙数，在调用finalizeBackbone后才能使用
     * @return 本次广播所花费的总时隙
     */
    private int calTransDelay() {
        int[] reachTime = new int[nodeCount];
        for (int i = 0; i < reachTime.length; i++)
            reachTime[i] = 0x3f3f3f3f;//初始化每个节点的到达时间为INF

        int currentNode, currentTime, maxTime = -1;
        Set<Integer> childSet, neighborSet;
        Queue<Integer> queue = new LinkedList<>();
        reachTime[0] = 0;//源节点的到达时间为0
        queue.offer(0);

        while (!queue.isEmpty()) {
            currentNode = queue.poll();
            childSet = getChildSet(currentNode);
            neighborSet = adjTable.get(currentNode);

            //当某节点在某个自己的传输时隙中广播时，注意到其邻节点中所有在当前时隙活跃的节点都能接收到信息，而不只是其覆盖节点
            for (Integer slot : nodeList[currentNode].getTransSet())
                for (Integer neighborId : neighborSet)
                    if (nodeList[neighborId].getActiveSlot() == slot) {
                        currentTime = reachTime[currentNode] + slot - reachTime[currentNode] % slotCount;
                        if (reachTime[currentNode] % slotCount >= slot) {
                            currentTime += slotCount;
                            if (currentNode == 0)
                                currentTime -= slotCount;
                        }
                        if (reachTime[neighborId] > currentTime)
                            reachTime[neighborId] = currentTime;
                    }

            for (Integer childId : childSet)
                queue.offer(childId);
        }

        for (int time : reachTime)
            if (time > maxTime)
                maxTime = time;//本短点可查看消息传送到各节点的时间

        return maxTime;
    }

    /**
     * 输出当前拓扑各计算过程的详细信息
     */
    private void getDetailedInfo() {
        finalizeBackbone();
        System.out.println("Id\tCovNodeId\tActiveSlot");
        for (Node n : nodeList)
            System.out.println(n.getId()+ "\t" + n.getCovNodeId() + "\t" + n.getActiveSlot());

        System.out.println();
        System.out.println("Backbone:");
        System.out.println("Id\tParent\tTransSet\tCoveringSet");
        for (Integer i : backbone) {
            System.out.print(i + "\t" + nodeList[i].getParentId() + "\t" + nodeList[i].getTransSet() + "\t[");
            for (Node n : nodeList[i].getCoveringSet())
                System.out.print(n.getId() + ",");
            System.out.println("]");
        }

        System.out.println();
        System.out.println("TransDelay: " + calTransDelay() + " slots");
        System.out.println("Total Transmission: " + calTotalTrans() + " times");
    }




    public static void main(String[] args) {
        Graph g = new Graph();
        g.getDetailedInfo();
        System.out.println();
        g.transformTopology();
        g.getDetailedInfo();

//        g.finalizeBackbone();
//        System.out.println("TransDelay: " + g.calTransDelay());
//        System.out.println("Total Transmission: " + g.calTotalTrans());
//
//        g.transformTopology();
//        g.finalizeBackbone();
//        System.out.println("TransDelay: " + g.calTransDelay());
//        System.out.println("Total Transmission: " + g.calTotalTrans());
    }
}
