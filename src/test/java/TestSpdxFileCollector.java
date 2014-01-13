import static org.junit.Assert.*;

import java.io.File;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.maven.SpdxDefaultFileInformation;
import org.spdx.maven.SpdxFileCollector;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXDocument;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;


public class TestSpdxFileCollector {
	
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSpdxFileCollector() {
	  //TODO: Implement test
	}

	@Test
	public void testCollectFilesInDirectory() {
		//TODO: Implement test
	}

	@Test
	public void testGetExtension() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		SPDXDocument spdxDoc = new SPDXDocument(model);
		SpdxFileCollector collector = new SpdxFileCollector(spdxDoc, 
				new Pattern[] {}, new SpdxDefaultFileInformation());
		File noExtension = new File("noextension");
		String result = collector.getExtension(noExtension);
		assertTrue(result.isEmpty());
		String ext = "abcd";
		File abcd = new File("fileName" + "." + ext);
		result = collector.getExtension(abcd);
		assertEquals(ext, result);
		File startsWithDot = new File(".configfile");
		result = collector.getExtension(startsWithDot);
		assertTrue(result.isEmpty());
		File multipleDots = new File ("file.with.more.dots."+ext);
		result = collector.getExtension(multipleDots);
		assertEquals(ext, result);
	}

	@Test
	public void testGetFiles() {
	  //TODO: Implement test
	}

	@Test
	public void testGetLicenseInfoFromFiles() {
	  //TODO: Implement test
	}

	@Test
	public void testGetVerificationCode() {
	  //TODO: Implement test
	}

	@Test
	public void testConvertChecksumToString() {
	  //TODO: Implement test
	}

}
