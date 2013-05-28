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
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.AbstractSubTask;
import hudson.model.queue.SubTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Keeps track of the number of executors that need to be consumed for this job.
 *
 * @author Kohsuke Kawaguchi
 */
public class HeavyJobProperty extends JobProperty<AbstractProject<?,?>> {
  public int weight;
  public final boolean allExecutorsEnabled;

  @DataBoundConstructor
  public HeavyJobProperty(final int weight, final boolean allExecutorsEnabled) {
    this.weight = weight;
    this.allExecutorsEnabled = allExecutorsEnabled;
  }

  @Override
  public List<SubTask> getSubTasks() {
    final List<SubTask> r = new ArrayList<SubTask>();

    // Store the weight before setting it to the maximum. So I can reset it
    // after.
    final int storedWeight = this.weight;
    if (allExecutorsEnabled) {
      this.weight = getExecutors();
    }
    for (int i=1; i< weight; i++) {
      r.add(new AbstractSubTask() {
        public Executable createExecutable() throws IOException {
          return new ExecutableImpl(this);
        }

        @Override
        public Object getSameNodeConstraint() {
          // must occupy the same node as the project itself
          return getProject();
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
    }
    this.weight = storedWeight;
    return r;
  }

  /**
   * @return Returns the number of executors available on the node.
   */
  private int getExecutors() {
    return Computer.currentComputer().getNumExecutors();
  }

  @Extension
  public static class DescriptorImpl extends JobPropertyDescriptor {
    @Override
    public String getDisplayName() {
      return Messages.HeavyJobProperty_DisplayName();
    }
  }

  public static class ExecutableImpl implements Executable {
    private final SubTask parent;
    private final Executor executor = Executor.currentExecutor();

    private ExecutableImpl(final SubTask parent) {
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

	@Override
	public long getEstimatedDuration() {
		return executor.getCurrentWorkUnit().context.getPrimaryWorkUnit().getExecutable().getEstimatedDuration();
	}
  }
}
