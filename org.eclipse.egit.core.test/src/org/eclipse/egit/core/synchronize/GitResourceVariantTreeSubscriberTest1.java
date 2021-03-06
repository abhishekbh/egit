/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.core.test.GitTestCase;
import org.eclipse.egit.core.test.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.IResourceVariantTree;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitResourceVariantTreeSubscriberTest1 extends GitTestCase {

	private Repository repo;

	private IProject iProject;

	private TestRepository testRepo;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		iProject = project.getProject();
		testRepo = new TestRepository(gitDir);
		testRepo.connect(iProject);
		repo = RepositoryMapping.getMapping(iProject).getRepository();
	}

	@After
	public void clearGitResources() throws Exception {
		testRepo.disconnect(iProject);
		testRepo.dispose();
		repo = null;
		super.tearDown();
	}

	/**
	 * This test simulates that user work and made some changes on branch 'test'
	 * and then try to synchronize "test" and 'master' branch.
	 *
	 * @throws Exception
	 */
	@Test
	public void shouldReturnSrcBranchAsBase() throws Exception {
		// when
		String fileName = "Main.java";
		File file = testRepo.createFile(iProject, fileName);
		RevCommit commit = testRepo.appendContentAndCommit(iProject, file,
				"class Main {}", "initial commit");
		IFile mainJava = testRepo.getIFile(iProject, file);
		testRepo.createAndCheckoutBranch(Constants.HEAD, Constants.R_HEADS
				+ "test");
		testRepo.appendContentAndCommit(iProject, file, "// test1",
				"secont commit");

		// given
		GitResourceVariantTreeSubscriber grvts = createGitResourceVariantTreeSubscriber(
				Constants.HEAD, Constants.R_HEADS + Constants.MASTER);
		grvts.getBaseTree();
		IResourceVariantTree baseTree = grvts.getBaseTree();

		// then
		IResourceVariant actual = commonAssertionsForBaseTree(baseTree,
				mainJava);
		assertEquals(commit.abbreviate(7).name() + "...",
				actual.getContentIdentifier());
	}

	private GitResourceVariantTreeSubscriber createGitResourceVariantTreeSubscriber(
			String src, String dst) throws IOException {
		GitSynchronizeData gsd = new GitSynchronizeData(repo, src, dst, false);
		GitSynchronizeDataSet gsds = new GitSynchronizeDataSet(gsd);
		new GitResourceVariantTreeSubscriber(gsds);
		return new GitResourceVariantTreeSubscriber(gsds);
	}

	private IResourceVariant commonAssertionsForBaseTree(
			IResourceVariantTree baseTree, IResource resource)
			throws TeamException {
		assertNotNull(baseTree);
		assertTrue(baseTree instanceof GitBaseResourceVariantTree);
		IResourceVariant resourceVariant = baseTree
				.getResourceVariant(resource);
		assertNotNull(resourceVariant);
		assertTrue(resourceVariant instanceof GitResourceVariant);
		return resourceVariant;
	}

}
