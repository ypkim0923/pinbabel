package backend.compliance;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithComplianceTest {

    @Test
    void verifiesApplicationModuleStructure() {
        ApplicationModules.of("com.ypkim.pinbabel").verify();
    }
}
