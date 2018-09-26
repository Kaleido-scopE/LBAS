import java.util.HashSet;
import java.util.Set;

public class Node {
    private Integer id;//节点编号
    private Integer parentId;//父节点编号
    private Integer activeSlot;//活跃时隙
    private Set<Integer> transSet;//传输时隙集合
    private Set<Node> coveringSet;//覆盖节点集合

    public Node(Integer id, Integer activeSlot) {
        this.id = id;
        this.activeSlot = activeSlot;
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

    public Integer getActiveSlot() {
        return activeSlot;
    }

    public void setActiveSlot(Integer activeSlot) {
        this.activeSlot = activeSlot;
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
