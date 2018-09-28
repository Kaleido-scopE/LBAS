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

    public Graph(Integer nodeCount, Integer slotCount) {
        this.nodeCount = nodeCount;
        this.slotCount = slotCount;
        backbone = new HashSet<>();
        maxLevel = 0;
        init();
    }

    private void init() {
        nodeList = new Node[nodeCount];
        adjTable = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++)
            adjTable.add(new HashSet<>());

        try {
            Scanner sc = new Scanner(new FileInputStream("./test_data(origin).txt"));

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

            calNodeLevel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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
        for (int i = 1; i < nodeCount; i++)
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
            nodeList[vId].setParentId(uId);
            nodeList[vId].setRootId(nodeList[uId].getRootId());
        }
    }

    /**
     * 自上而下遍历，将各覆盖节点加入广播主干，并建立数棵覆盖子树
     */
    private void constructSubTrees() {
        //列表中第i个集合对应第i个时隙的覆盖节点集合
        //同时可以计算各节点的覆盖节点
        List<Set<Node>> coveringNodeSetList = new ArrayList<>();
        for (int i = 0; i < slotCount; i++)
            coveringNodeSetList.add(getCoveringSlotNodeSet(i));
        Node u;
        Set<Node> Sl;

        backbone.add(0);//将源点加入广播骨架

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

    public static void main(String[] args) {

//        Graph g = new Graph(20, 3);
//        g.getCoveringSlotNodeSet(0);
//        g.getCoveringSlotNodeSet(1);
//        g.getCoveringSlotNodeSet(2);
        Set<Integer> s = new HashSet<>();
        s.add(1);
        s.add(2);
        s.add(3);
        for (Integer i : s)
            if (i == 2)
                s.remove(i);
        System.out.println(s);
//        for (int i = 0; i < g.nodeCount; i++)
//            System.out.println(g.nodeList[i].getId() + " " + g.nodeList[i].getCovNodeId());

//        Set<Node> set = g.getLevelBasedSet(5);
//        for (Node n : set)
//            System.out.println(n.getId());

//        Set<Node> nodeSet = g.getCoveringSlotNodeSet(2);
//        for (Node n : nodeSet)
//            System.out.println(n.getId());
//        Set<Node> nodeSet1 = g.nodeList[9].getCoveringSet();
//        for (Node n : nodeSet1)
//            System.out.println(n.getId());
    }
}
