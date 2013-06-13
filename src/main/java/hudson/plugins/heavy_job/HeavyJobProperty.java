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
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.AbstractSubTask;
import hudson.model.queue.SubTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Keeps track of the number of executors that need to be consumed for this job.
 * 
 * @author Kohsuke Kawaguchi
 */
public class HeavyJobProperty extends JobProperty<AbstractProject<?, ?>> {
	public int weight;
	public final boolean allExecutorsEnabled;

	private static final Logger LOGGER = Logger
			.getLogger(HeavyJobProperty.class.getName());

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

		// Only if allExecutors is checked will we attempt to set the weight.
		// Otherwise, we just use weight that was passed in.
		if (allExecutorsEnabled) {
			LOGGER.fine("Got allExecutorsEnabled setting weight");
			this.weight = getExecutors();
			LOGGER.fine("Job is: " + this.owner.getDisplayName());
			LOGGER.fine("Weight is: " + this.weight);
		}
		for (int i = 1; i < weight; i++) {
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
					return Messages
							.HeavyJobProperty_SubTaskDisplayName(getProject()
									.getDisplayName());
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
		Node node = null;

		// Get the label. Have to get the QueueItem if the build goes to Queue.
		Label label = (HeavyJobProperty.this.owner.getAssignedLabel() != null) ? HeavyJobProperty.this.owner
				.getAssignedLabel() : HeavyJobProperty.this.owner
				.getQueueItem().getAssignedLabel();

		// Check to see it got the label object. Since we want to be certain we
		// get the correct node, we check to make sure that the label is a self
		// label. Also check if the label is master. When master we can't use
		// node, so we get the Jenkins instance for master executor count. If
		// it's not a self label or master, we default to the weight passed in.
		if (label != null) {
			if (label.isSelfLabel()
					&& !label.getName().equalsIgnoreCase("master")) {
				LOGGER.fine("Label: " + label.toString());
				node = Jenkins.getInstance().getNode(label.getName());
				if (node != null) {
					LOGGER.fine("Number of executors: "
							+ node.getNumExecutors());
					LOGGER.fine("Name of node: " + node.getNodeName());
					LOGGER.fine("Name of job: "
							+ HeavyJobProperty.this.owner.getDisplayName());
				} else {
					LOGGER.fine("Node was null, returning weight.");
					return this.weight;
				}
			} else if (label.getName().equalsIgnoreCase("master")) {
				LOGGER.fine("Number of executors: "
						+ Jenkins.getInstance().getNumExecutors());
				LOGGER.fine("Name of node: "
						+ Jenkins.getInstance().getDisplayName());
				LOGGER.fine("Name of job: "
						+ HeavyJobProperty.this.owner.getDisplayName());
				return Jenkins.getInstance().getNumExecutors();
			} else {
				return this.weight;
			}
		} else {
			LOGGER.fine("Label was null, returning weight.");
			return this.weight;
		}

		return node.getNumExecutors();
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

		public AbstractBuild<?, ?> getBuild() {
			return (AbstractBuild<?, ?>) executor.getCurrentWorkUnit().context
					.getPrimaryWorkUnit().getExecutable();
		}

		public void run() {
			// nothing. we just waste time
		}

		@Override
		public long getEstimatedDuration() {
			return parent.getEstimatedDuration();
		}

	}
}
