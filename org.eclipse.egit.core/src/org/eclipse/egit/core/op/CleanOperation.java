/*******************************************************************************
 * Copyright (C) 2011, Chris Aniszczyk <zx@redhat.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.op;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.api.CleanCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

/**
 * Clean operation cleans a repository or a selected list of resources
 */
public class CleanOperation implements IEGitOperation {

	private IResource[] resources;

	private ISchedulingRule schedulingRule;

	/**
	 * Construct an CleanOperation
	 *
	 * @param resources
	 */
	public CleanOperation(IResource[] resources) {
		this.resources = new IResource[resources.length];
		System.arraycopy(resources, 0, this.resources, 0, resources.length);
		schedulingRule = calcSchedulingRule();
	}

	public void execute(IProgressMonitor monitor) throws CoreException {
		// TODO run clean command
		discoverRepos();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * @param res
	 * @param repository
	 * @throws GitAPIException
	 */
	private void cleanUp (IResource res, Repository repository) throws GitAPIException {
		//String resRelPath = RepositoryMapping.getMapping(res).getRepoRelativePath(res);
		CleanCommand clean = new Git(repository).clean();
		clean.call();
	}

	/**
	 * @throws CoreException
	 */
	public void discoverRepos() throws CoreException {
		for (IResource res : resources) {
			Repository repo = getRepository(res);
			try {
				cleanUp(res, repo);
			} catch (GitAPIException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return Set
	 */
	public Set<String> dryRun() {
		Repository repo = getRepository(resources[0]);
		CleanCommand clean = new Git(repo).clean();

		return clean.dryRun();
	}

	private static Repository getRepository(IResource resource) {
		IProject project = resource.getProject();
		RepositoryMapping repositoryMapping = RepositoryMapping.getMapping(project);
		if (repositoryMapping != null)
			return repositoryMapping.getRepository();
		else
			return null;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public ISchedulingRule getSchedulingRule() {
		return schedulingRule;
	}

	private ISchedulingRule calcSchedulingRule() {
		List<ISchedulingRule> rules = new ArrayList<ISchedulingRule>();
		IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace()
				.getRuleFactory();
		for (IResource resource : resources) {
			IContainer container = resource.getParent();
			if (!(container instanceof IWorkspaceRoot)) {
				ISchedulingRule rule = ruleFactory.modifyRule(container);
				if (rule != null)
					rules.add(rule);
			}
		}
		if (rules.size() == 0)
			return null;
		else
			return new MultiRule(rules.toArray(new IResource[rules.size()]));
	}
}