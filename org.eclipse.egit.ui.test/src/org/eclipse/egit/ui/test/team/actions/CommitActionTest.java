/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *    Chris Aniszczyk <caniszczyk@gmail.com> - tag API changes
 *******************************************************************************/
package org.eclipse.egit.ui.test.team.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.op.BranchOperation;
import org.eclipse.egit.core.op.TagOperation;
import org.eclipse.egit.ui.common.CommitDialogTester;
import org.eclipse.egit.ui.common.CommitDialogTester.NoFilesToCommitPopup;
import org.eclipse.egit.ui.common.LocalRepositoryTestCase;
import org.eclipse.egit.ui.test.CommitMessageUtil;
import org.eclipse.egit.ui.test.TestUtil;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotPerspective;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the Team->Commit action
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class CommitActionTest extends LocalRepositoryTestCase {
	private static File repositoryFile;

	private static SWTBotPerspective perspective;

	@BeforeClass
	public static void setup() throws Exception {
		repositoryFile = createProjectAndCommitToRepository();
		Repository repo = lookupRepository(repositoryFile);
		TestUtil.configureTestCommitterAsUser(repo);
		// TODO delete the second project for the time being (.gitignore is
		// currently not hiding the .project file from commit)
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJ2);
		File dotProject = new File(project.getLocation().toOSString(), ".project");
		project.delete(false, false, null);
		assertTrue(dotProject.delete());

		TagBuilder tag = new TagBuilder();
		tag.setTag("SomeTag");
		tag.setTagger(RawParseUtils.parsePersonIdent(TestUtil.TESTAUTHOR));
		tag.setMessage("I'm just a little tag");
		tag.setObjectId(repo.resolve(repo.getFullBranch()), Constants.OBJ_COMMIT);
		TagOperation top = new TagOperation(repo, tag, false);
		top.execute(null);
		touchAndSubmit(null);

		perspective = bot.activePerspective();
		bot.perspectiveById("org.eclipse.pde.ui.PDEPerspective").activate();
		waitInUI();
	}

	@AfterClass
	public static void shutdown() {
		perspective.activate();
	}

	@Before
	public void prepare() throws Exception {
		Repository repo = lookupRepository(repositoryFile);
		if (!repo.getBranch().equals("master")) {
			BranchOperation bop = new BranchOperation(repo, "refs/heads/master");
			bop.execute(null);
		}
	}

	@Test
	public void testOpenCommitWithoutChanged() throws Exception {
		NoFilesToCommitPopup popup = CommitDialogTester
				.openCommitDialogExpectNoFilesToCommit(PROJ1);
		popup.cancelPopup();
	}

	@Test
	public void testCommitSingleFile() throws Exception {
		setTestFileContent("I have changed this");
		CommitDialogTester commitDialogTester = CommitDialogTester
				.openCommitDialog(PROJ1);
		assertEquals("Wrong row count", 1, commitDialogTester.getRowCount());
		assertTrue("Wrong file",
				commitDialogTester.getEntryText(0).endsWith("test.txt"));
		commitDialogTester.setAuthor(TestUtil.TESTAUTHOR);
		commitDialogTester.setCommitter(TestUtil.TESTCOMMITTER);
		commitDialogTester.setCommitMessage("The new commit");
		commitDialogTester.commit();
		TestUtil.checkHeadCommit(lookupRepository(repositoryFile),
				TestUtil.TESTAUTHOR, TestUtil.TESTCOMMITTER, "The new commit");
		NoFilesToCommitPopup popup = CommitDialogTester
				.openCommitDialogExpectNoFilesToCommit(PROJ1);
		popup.cancelPopup();
	}

	@Test
	public void testAmendWithChangeIdPreferenceOff() throws Exception {
		Repository repo = lookupRepository(repositoryFile);
		repo.getConfig().setBoolean(ConfigConstants.CONFIG_GERRIT_SECTION,
				null, ConfigConstants.CONFIG_KEY_CREATECHANGEID, true);
		setTestFileContent("Another Change");
		CommitDialogTester commitDialogTester = CommitDialogTester
				.openCommitDialog(PROJ1);
		assertEquals("Wrong row count", 1, commitDialogTester.getRowCount());
		assertTrue("Wrong file",
				commitDialogTester.getEntryText(0).endsWith("test.txt"));
		commitDialogTester.setAuthor(TestUtil.TESTAUTHOR);
		commitDialogTester.setCommitter(TestUtil.TESTCOMMITTER);
		String commitMessage = commitDialogTester.getCommitMessage();
		assertTrue(commitMessage.indexOf("Change-Id") > 0);
		String newCommitMessage = "Change to be amended \n\n" + commitMessage;
		commitDialogTester.setCommitMessage(newCommitMessage);
		commitDialogTester.commit();
		NoFilesToCommitPopup noFilesToCommitPopup = CommitDialogTester
				.openCommitDialogExpectNoFilesToCommit(PROJ1);
		repo.getConfig().setBoolean(ConfigConstants.CONFIG_GERRIT_SECTION,
				null, ConfigConstants.CONFIG_KEY_CREATECHANGEID, false);
		commitDialogTester = noFilesToCommitPopup.confirmPopup();
		assertTrue(commitDialogTester.getCommitMessage().indexOf("Change-Id") > 0);
	}

	@Test
	public void testLaunchedWithAmend() throws Exception {
		Repository repository = lookupRepository(repositoryFile);
		RevCommit oldHeadCommit = TestUtil.getHeadCommit(repository);
		commitOneFileChange("Again another Change");
		ObjectId headCommitId = TestUtil.getHeadCommit(repository).getId();
		NoFilesToCommitPopup noFilesToCommitPopup = CommitDialogTester
				.openCommitDialogExpectNoFilesToCommit(PROJ1);
		CommitDialogTester commitDialogTester = noFilesToCommitPopup.confirmPopup();
		assertTrue(commitDialogTester.getCommitMessage().indexOf("Change-Id") > 0);
		assertTrue(commitDialogTester.getCommitMessage().indexOf("Signed-off-by") > 0);
		assertTrue(commitDialogTester.getAmend());
		assertTrue(commitDialogTester.getSignedOff());
		assertTrue(commitDialogTester.getInsertChangeId());
		// change commit message to get a different SHA1 for the commit
		commitDialogTester.setCommitMessage("Changed "
				+ commitDialogTester.getCommitMessage());
		commitDialogTester.commit();
		RevCommit headCommit = TestUtil.getHeadCommit(repository);
		if(headCommitId.equals(headCommit.getId()))
			fail("There is no new commit");
		assertEquals(oldHeadCommit, headCommit.getParent(0));
	}

	private void commitOneFileChange(String fileContent) throws Exception {
		setTestFileContent(fileContent);
		CommitDialogTester commitDialogTester = CommitDialogTester
				.openCommitDialog(PROJ1);
		assertEquals("Wrong row count", 1, commitDialogTester.getRowCount());
		assertTrue("Wrong file",
				commitDialogTester.getEntryText(0).endsWith("test.txt"));
		commitDialogTester.setAuthor(TestUtil.TESTAUTHOR);
		commitDialogTester.setCommitter(TestUtil.TESTCOMMITTER);
		commitDialogTester.setCommitMessage("Commit message");
		commitDialogTester.setInsertChangeId(true);
		commitDialogTester.setSignedOff(true);

		String commitMessage = commitDialogTester.getCommitMessage();
		assertTrue(commitMessage.indexOf("Change-Id") > 0);
		assertTrue(commitMessage.indexOf("Signed-off-by") > 0);
		commitDialogTester.commit();
	}

	@Test
	public void testAmend() throws Exception {
		Repository repository = lookupRepository(repositoryFile);
		RevCommit oldHeadCommit = TestUtil.getHeadCommit(repository);
		commitOneFileChange("Yet another Change");
		RevCommit headCommit = TestUtil.getHeadCommit(repository);
		ObjectId headCommitId = headCommit.getId();
		String changeId = CommitMessageUtil.extractChangeId(headCommit
				.getFullMessage());
		setTestFileContent("Changes over changes");
		CommitDialogTester commitDialogTester = CommitDialogTester
				.openCommitDialog(PROJ1);
		commitDialogTester.setAmend(true);
		assertTrue(commitDialogTester.getCommitMessage().indexOf("Change-Id") > 0);
		assertTrue(commitDialogTester.getCommitMessage().indexOf(
				"Signed-off-by") > 0);
		assertTrue(commitDialogTester.getSignedOff());
		assertTrue(commitDialogTester.getInsertChangeId());
		commitDialogTester.commit();
		headCommit = TestUtil.getHeadCommit(repository);
		if(headCommitId.equals(headCommit.getId()))
			fail("There is no new commit");
		assertEquals(oldHeadCommit, headCommit.getParent(0));
		assertTrue(headCommit.getFullMessage().indexOf(changeId) > 0);
	}

}
