package br.com.familhas.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Todo {@code crm:...} escrito num {@code @PreAuthorize} existe na matriz de papéis (erro de digitação = 403 eterno). */
class CrmPermissionLiteralsTests {

    private static final Pattern LITERAL = Pattern.compile("'(crm:[^']+)'");

    @Test
    void everyCrmPermissionUsedInPreAuthorizeExists() throws Exception {
        List<String> used = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("br.com.familhas")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            collect(type.getAnnotation(PreAuthorize.class), used);
            for (Method method : type.getDeclaredMethods()) {
                collect(method.getAnnotation(PreAuthorize.class), used);
            }
        }
        assertThat(CrmRole.ADMIN.permissions()).containsAll(used);
    }

    private static void collect(PreAuthorize annotation, List<String> target) {
        if (annotation == null) {
            return;
        }
        Matcher matcher = LITERAL.matcher(annotation.value());
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }
}
