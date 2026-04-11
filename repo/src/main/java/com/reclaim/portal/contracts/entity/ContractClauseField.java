package com.reclaim.portal.contracts.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "contract_clause_fields")
public class ContractClauseField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_version_id")
    private Long templateVersionId;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "field_type", length = 50)
    private String fieldType;

    @Column(name = "field_label", length = 255)
    private String fieldLabel;

    private boolean required;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "display_order")
    private int displayOrder;

    public ContractClauseField() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemplateVersionId() {
        return templateVersionId;
    }

    public void setTemplateVersionId(Long templateVersionId) {
        this.templateVersionId = templateVersionId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
