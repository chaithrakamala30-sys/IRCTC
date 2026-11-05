import { PassengerIdentityExperience } from "../../../components/identity-experience";

export const metadata = { title: "Sign in — Mobile passenger" };

export default function MobileLoginPage() {
  return <PassengerIdentityExperience device="mobile" mode="login" />;
}
