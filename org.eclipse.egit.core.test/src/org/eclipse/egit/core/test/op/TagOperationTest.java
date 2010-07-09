package org.eclipse.egit.core.test.op;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.op.TagOperation;
import org.eclipse.egit.core.test.DualRepositoryTestCase;
import org.eclipse.egit.core.test.TestRepository;
import org.eclipse.egit.core.test.TestUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Tag;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TagOperationTest extends DualRepositoryTestCase {

	File workdir;

	String projectName = "TagTest";

	@Before
	public void setUp() throws Exception {

		workdir = testUtils.getTempDir("Repository1");

		repository1 = new TestRepository(new File(workdir, Constants.DOT_GIT));

		// now we create a project in repo1
		IProject project = testUtils.createProjectInLocalFileSystem(workdir,
				projectName);
		testUtils.addFileToProject(project, "folder1/file1.txt", "Hello world");

		repository1.connect(project);

		project.accept(new IResourceVisitor() {

			public boolean visit(IResource resource) throws CoreException {
				if (resource instanceof IFile) {
					try {
						repository1
								.track(EFS.getStore(resource.getLocationURI())
										.toLocalFile(0, null));
					} catch (IOException e) {
						throw new CoreException(Activator.error(e.getMessage(),
								e));
					}
				}
				return true;
			}
		});

		repository1.commit("Initial commit");
	}

	@After
	public void tearDown() throws Exception {
		repository1.dispose();
		repository1 = null;
		testUtils.deleteRecursive(workdir);
	}

	@Test
	public void addTag() throws Exception {
		assertTrue("Tags should be empty", repository1.getRepository().getTags().isEmpty());
		Tag newTag = new Tag(repository1.getRepository());
		newTag.setTag("TheNewTag");
		newTag.setMessage("Well, I'm the tag");
		newTag.setAuthor(new PersonIdent(TestUtils.AUTHOR));
		newTag.setObjId(repository1.getRepository().resolve("refs/heads/master"));
		TagOperation top = new TagOperation(repository1.getRepository(), newTag, false);
		top.execute(new NullProgressMonitor());
		assertFalse("Tags should not be empty", repository1.getRepository().getTags().isEmpty());

		try {
			top.execute(null);
			fail("Expected Exception not thrown");
		} catch (CoreException e) {
			// expected
		}

		top = new TagOperation(repository1.getRepository(), newTag, true);
		try {
			top.execute(null);
			fail("Expected Exception not thrown");
		} catch (CoreException e) {
			// expected
		}
		Ref tagRef = repository1.getRepository().getTags().get("TheNewTag");
		Tag tag = repository1.getRepository().mapTag(tagRef.getName());
		newTag.setMessage("Another message");
		assertFalse("Messages should differ", tag.getMessage().equals(
				newTag.getMessage()));
		top.execute(null);
		tag = repository1.getRepository().mapTag(tagRef.getName());
		assertTrue("Messages be same", tag.getMessage().equals(
				newTag.getMessage()));
	}

}