package hudson.plugins.heavy_job;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.AbstractSubTask;
import hudson.model.queue.SubTask;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixConfiguration;
import hudson.model.queue.SubTaskContributor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Extension
public class HeavyJobSubTaskContributor extends SubTaskContributor {

    @Override
    public List<SubTask> forProject(AbstractProject<?,?> p) {
        // For MatrixProject, we add placeholder tasks to individual
        // build configurations after they are created by the main task
        if (p instanceof MatrixProject)
            return Collections.emptyList();

        AbstractProject<?,?> mainProject = p;
        if (p instanceof MatrixConfiguration)
            mainProject = ((MatrixConfiguration)p).getParent();

        HeavyJobProperty heavyJobProperty =
                mainProject.getProperty(HeavyJobProperty.class);

        if (heavyJobProperty == null || heavyJobProperty.weight <= 1)
            return Collections.emptyList();

        List<SubTask> r = new ArrayList<SubTask>();
        for (int i=1; i<heavyJobProperty.weight; i++)
            r.add(new HeavyWeightSubTask(p));

        return r;
    }

    public static class ExecutableImpl implements Executable {
        private final SubTask parent;
        private final Executor executor = Executor.currentExecutor();

        private ExecutableImpl(SubTask parent) {
            this.parent = parent;
        }

        public SubTask getParent() {
            return parent;
        }

        public long getEstimatedDuration() {
            return parent.getEstimatedDuration();
        }

        public AbstractBuild<?,?> getOwnerExecutable() {
            return (AbstractBuild<?,?>)executor.getCurrentWorkUnit()
                    .context.getPrimaryWorkUnit().getExecutable();
        }

        public void run() {
            // nothing. we just waste time
        }
    }

    public static class HeavyWeightSubTask extends AbstractSubTask {
        private final AbstractProject<?, ?> project;

        public HeavyWeightSubTask(AbstractProject<?, ?> project) {
            this.project = project;
        }

        public Executable createExecutable() throws IOException {
            return new ExecutableImpl(this);
        }

        @Override
        public Object getSameNodeConstraint() {
            // must occupy the same node as the project itself
            return project;
        }

        @Override
        public long getEstimatedDuration() {
            return project.getEstimatedDuration();
        }

        public Task getOwnerTask() {
            return project;
        }

        public String getDisplayName() {
            return Messages.HeavyJobProperty_SubTaskDisplayName(
                                        project.getDisplayName());
        }
    }
}
