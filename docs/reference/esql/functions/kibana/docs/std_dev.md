<!--
This is generated by ESQL's AbstractFunctionTestCase. Do no edit it. See ../README.md for how to regenerate it.
-->

### STD_DEV
The standard deviation of a numeric field.

```
FROM employees
| STATS STD_DEV(height)
```