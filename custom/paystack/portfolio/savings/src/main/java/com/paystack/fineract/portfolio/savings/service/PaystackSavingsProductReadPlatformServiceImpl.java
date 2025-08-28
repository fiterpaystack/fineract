package com.paystack.fineract.portfolio.savings.service;

import com.paystack.fineract.portfolio.savings.data.PaystackSavingsProductAdditionalAttributes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.data.SavingsProductData;
import org.apache.fineract.portfolio.savings.exception.SavingsProductNotFoundException;
import org.apache.fineract.portfolio.savings.service.SavingsProductReadPlatformServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackSavingsProductReadPlatformServiceImpl extends SavingsProductReadPlatformServiceImpl {

    private final PaystackSavingsProductMapper paystackSavingsProductMapper = new PaystackSavingsProductMapper();

    public PaystackSavingsProductReadPlatformServiceImpl(PlatformSecurityContext context, JdbcTemplate jdbcTemplate,
            FineractEntityAccessUtil fineractEntityAccessUtil) {
        super(context, jdbcTemplate, fineractEntityAccessUtil);
    }

    @Override
    public SavingsProductData retrieveOne(final Long savingProductId) {
        try {
            this.context.authenticatedUser();
            final String additionalParam = " sp.is_emt_levy_applicable as emtLevyApplicable, sp.emt_levy_amount as emtLevyAmount,"
                    + " sp.override_global_emt_levy as overrideGlobalEmtLevy, sp.emt_levy_threshold as emtLevyThreshold, ";

            final String sql = "select " + additionalParam + this.savingsProductRowMapper.schema()
                    + " where sp.id = ? and sp.deposit_type_enum = ?";

            return this.jdbcTemplate.queryForObject(sql, this.paystackSavingsProductMapper, savingProductId,
                    DepositAccountType.SAVINGS_DEPOSIT.getValue());
        } catch (final EmptyResultDataAccessException e) {
            throw new SavingsProductNotFoundException(savingProductId, e);
        }
    }

    private final class PaystackSavingsProductMapper implements RowMapper<SavingsProductData> {

        @Override
        public SavingsProductData mapRow(ResultSet rs, int rowNum) throws SQLException {
            // First map using parent mapper
            SavingsProductData base = savingsProductRowMapper.mapRow(rs, rowNum);
            HashMap<String, Object> additionalAttriubtes = new HashMap<>();
            additionalAttriubtes.put(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT, rs.getBigDecimal("emtLevyAmount"));
            additionalAttriubtes.put(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE, rs.getBoolean("emtLevyApplicable"));
            additionalAttriubtes.put(PaystackSavingsProductAdditionalAttributes.OVERRIDE_GLOBAL_EMT_LEVY,
                    rs.getBoolean("overrideGlobalEmtLevy"));
            additionalAttriubtes.put(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD, rs.getBigDecimal("emtLevyThreshold"));

            base.setAdditionalAttributes(additionalAttriubtes);

            return base;
        }
    }
}
