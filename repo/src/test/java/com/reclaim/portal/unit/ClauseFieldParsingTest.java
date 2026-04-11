package com.reclaim.portal.unit;

import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.contracts.entity.ContractClauseField;
import com.reclaim.portal.contracts.service.ContractService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for contract clause field parsing with JSON format, edge cases,
 * and required field validation.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClauseFieldParsingTest {

    @Autowired
    private ContractService contractService;

    @Test
    void shouldParseJsonObjectFormat() {
        Map<String, String> result = contractService.parseFieldValues(
                "{\"name\":\"John Doe\",\"address\":\"123 Main St\"}");
        assertThat(result).containsEntry("name", "John Doe");
        assertThat(result).containsEntry("address", "123 Main St");
    }

    @Test
    void shouldHandleJsonValuesContainingCommas() {
        Map<String, String> result = contractService.parseFieldValues(
                "{\"address\":\"123 Main St, Suite 4\",\"name\":\"Doe, John\"}");
        assertThat(result).containsEntry("address", "123 Main St, Suite 4");
        assertThat(result).containsEntry("name", "Doe, John");
    }

    @Test
    void shouldHandleJsonValuesContainingColons() {
        Map<String, String> result = contractService.parseFieldValues(
                "{\"time\":\"10:30 AM\",\"ratio\":\"1:2:3\"}");
        assertThat(result).containsEntry("time", "10:30 AM");
        assertThat(result).containsEntry("ratio", "1:2:3");
    }

    @Test
    void shouldHandleJsonValuesContainingQuotes() {
        Map<String, String> result = contractService.parseFieldValues(
                "{\"note\":\"He said \\\"hello\\\"\"}");
        assertThat(result).containsEntry("note", "He said \"hello\"");
    }

    @Test
    void shouldParseLegacyKeyValueFormat() {
        Map<String, String> result = contractService.parseFieldValues("name=John,city=Springfield");
        assertThat(result).containsEntry("name", "John");
        assertThat(result).containsEntry("city", "Springfield");
    }

    @Test
    void shouldReturnEmptyMapForNullInput() {
        Map<String, String> result = contractService.parseFieldValues(null);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapForBlankInput() {
        Map<String, String> result = contractService.parseFieldValues("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldValidateRequiredFieldsMissingFromExplicitInput() {
        ContractClauseField requiredField = new ContractClauseField();
        requiredField.setFieldName("partyName");
        requiredField.setRequired(true);
        requiredField.setDefaultValue(null);

        // When explicit JSON is provided but required field is missing, it should fail
        assertThatThrownBy(() ->
                contractService.renderContent("Contract for {{partyName}}", "{}", List.of(requiredField)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("partyName");
    }

    @Test
    void shouldRejectNullFieldValuesWhenRequiredFieldHasNoDefault() {
        ContractClauseField requiredField = new ContractClauseField();
        requiredField.setFieldName("partyName");
        requiredField.setRequired(true);
        requiredField.setDefaultValue(null);

        // Required fields are always enforced — null fieldValues with no default throws
        assertThatThrownBy(() ->
            contractService.renderContent("Contract for {{partyName}}", null, List.of(requiredField))
        ).isInstanceOf(com.reclaim.portal.common.exception.BusinessRuleException.class)
         .hasMessageContaining("partyName");
    }

    @Test
    void shouldAllowRequiredFieldWithDefault() {
        ContractClauseField field = new ContractClauseField();
        field.setFieldName("currency");
        field.setRequired(true);
        field.setDefaultValue("USD");

        String result = contractService.renderContent("Payment in {{currency}}", "{}", List.of(field));
        assertThat(result).isEqualTo("Payment in USD");
    }

    @Test
    void shouldRenderJsonFieldValues() {
        ContractClauseField nameField = new ContractClauseField();
        nameField.setFieldName("name");
        nameField.setRequired(false);
        nameField.setDefaultValue("");

        ContractClauseField addressField = new ContractClauseField();
        addressField.setFieldName("address");
        addressField.setRequired(false);
        addressField.setDefaultValue("");

        String result = contractService.renderContent(
                "Name: {{name}}, Address: {{address}}",
                "{\"name\":\"Jane, Jr.\",\"address\":\"456 Oak Ave, Apt 7\"}",
                List.of(nameField, addressField));

        assertThat(result).isEqualTo("Name: Jane, Jr., Address: 456 Oak Ave, Apt 7");
    }
}
