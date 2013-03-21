/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.heavy_job;

import hudson.Extension;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.EnvironmentList;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.AbstractSubTask;
import hudson.model.queue.SubTask;
import hudson.model.queue.WorkUnit;
import hudson.model.queue.WorkUnitContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Keeps track of the number of executors that need to be consumed for this job.
 *
 * @author Kohsuke Kawaguchi
 */
public class HeavyJobProperty extends JobProperty<AbstractProject<?,?>> {
    
    private static final Logger LOGGER = Logger.getLogger(HeavyJobProperty.class.getName());
    
    public final int weight;
    public final boolean sameNode;

    @DataBoundConstructor
    public HeavyJobProperty(int weight, boolean sameNode) {
        this.weight = weight;
        this.sameNode = sameNode;
    }

    @Override
    public boolean prebuild(final AbstractBuild<?, ?> build, BuildListener listener) {
        EnvironmentList environments = build.getEnvironments();
        environments.add(new Environment() {

            @Override
            public void buildEnvVars(Map<String, String> env) {
                Executor executor = Executor.currentExecutor();
                WorkUnitContext context = executor.getCurrentWorkUnit().context;
                List<WorkUnit> workUnits = context.getWorkUnits();
                //Collection<? extends SubTask> subTasks = Tasks.getSubTasksOf(context.task);
                StringBuilder b = new StringBuilder();
                for (Iterator<WorkUnit> i = workUnits.iterator(); i.hasNext(); ) {
                    WorkUnit unit = i.next();
                    try {
                        b.append(unit.getExecutor().getOwner().getHostName());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (i.hasNext()) {
                        b.append(",");
                    }
                }
                LOGGER.info(build.getFullDisplayName() + " executors IP addresses: " + b);
                env.put("EXECUTORS_IPS", b.toString());
            }
            
        });
        return true;
    }

    @Override
    public List<SubTask> getSubTasks() {
        List<SubTask> r = new ArrayList<SubTask>();
        for (int i=1; i< weight; i++)
            r.add(new AbstractSubTask() {
                public Executable createExecutable() throws IOException {
                    return new ExecutableImpl(this);
                }

                @Override
                public Object getSameNodeConstraint() {
                    // must occupy the same node as the project itself
                    return sameNode ? getProject() : null;
                }

                @Override
                public long getEstimatedDuration() {
                    return getProject().getEstimatedDuration();
                }

                public Task getOwnerTask() {
                    return getProject();
                }

                public String getDisplayName() {
                    return Messages.HeavyJobProperty_SubTaskDisplayName(getProject().getDisplayName());
                }

                private AbstractProject<?, ?> getProject() {
                    return HeavyJobProperty.this.owner;
                }
            });
        return r;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.HeavyJobProperty_DisplayName();
        }
        
        @Override
        public boolean isApplicable(Class<? extends Job> job) {
            return true;
        }
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

        public AbstractBuild<?,?> getBuild() {
            return (AbstractBuild<?,?>)executor.getCurrentWorkUnit().context.getPrimaryWorkUnit().getExecutable();
        }

        public void run() {
            // nothing. we just waste time
        }

        public long getEstimatedDuration() {
            return parent.getEstimatedDuration();
        }
    }
}
