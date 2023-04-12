import org.apache.zookeeper.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class DistributedLock {
    private final String zkHost;
    private final String lockName;
    private final String lockPath;
    private ZooKeeper zooKeeper;
    private String currentLockNode;

    public DistributedLock(String zkHost, String lockName) throws IOException, InterruptedException, KeeperException {
        this.zkHost = zkHost;
        this.lockName = lockName;
        this.lockPath = "/locks/" + lockName;
        this.zooKeeper = new ZooKeeper(zkHost, 10000, new Watcher() {
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.Expired) {
                    // handle session expiration
                }
            }
        });
        ensureLockPathExists();
    }

    private void ensureLockPathExists() throws KeeperException, InterruptedException {
        if (zooKeeper.exists(lockPath, false) == null) {
            zooKeeper.create(lockPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    public void acquire() throws KeeperException, InterruptedException {
        currentLockNode = zooKeeper.create(lockPath + "/node_", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        waitUntilAcquired();
    }

    private void waitUntilAcquired() throws KeeperException, InterruptedException {
        while (true) {
            String[] children = zooKeeper.getChildren(lockPath, false).toArray(new String[0]);
            int currentIndex = getCurrentNodeIndex(children);
            if (currentIndex == 0) {
                // lock acquired
                return;
            }
            String previousNode = lockPath + "/" + children[currentIndex - 1];
            final CountDownLatch latch = new CountDownLatch(1);
            zooKeeper.exists(previousNode, new Watcher() {
                public void process(WatchedEvent event) {
                    latch.countDown();
                }
            });
            latch.await();
        }
    }

    private int getCurrentNodeIndex(String[] children) {
        for (int i = 0; i < children.length; i++) {
            if (currentLockNode.endsWith(children[i])) {
                return i;
            }
        }
        throw new IllegalStateException("Current node not found in children list");
    }

    public void release() throws KeeperException, InterruptedException {
        zooKeeper.delete(currentLockNode, -1);
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }
}
